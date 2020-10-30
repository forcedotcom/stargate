/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.stargate.utils

import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}
import pdi.jwt.{Jwt, JwtAlgorithm}
import play.api.Configuration

trait JwtUtil {
  def decode(jwt: String): Try[(String, String, String)]

  def encode(jwtPayload: String): String

  def mockJwt(): String

  def mockDecodedJwt(): String
}

@Singleton
class JwtUtilImpl @Inject() (config: Configuration) extends JwtUtil {
  val secretKey = config.get[String]("com.salesforce.mce.stargate.marketingCloud.secretKey")

  // secretKeyCollection for projects/instances that have this configured
  // If this configuration is not present copy secretKey to this collection
  val secretKeyCollection = config.getOptional[Seq[String]]("com.salesforce.mce.stargate.marketingCloud.additionalSecretKeys") match {
    case Some(keys) => secretKey +: keys
    case None       => Seq(secretKey)
  }

  override def mockDecodedJwt: String =
    s"""
       | {
       |  "exp": ${System.currentTimeMillis()},
       |  "jti": "JT-IXXXXXXXXXXXXXXXXXXXXXXX",
       |  "request": {
       |    "claimsVersion": 2,
       |    "user": {
       |      "id": 1111118,
       |      "email": "stargate@salesforce.com",
       |      "culture": "en-US",
       |      "timezone": {
       |        "longName": "(GMT+08:00) Kuala Lumpur, Singapore",
       |        "shortName": "GMT+8",
       |        "offset": 8.0,
       |        "dst": false
       |      }
       |    },
       |    "rest": {
       |      "authEndpoint": "https://auth-s8.exacttargetapis.com/v1/requestToken",
       |      "apiEndpointBase": "https://www.exacttargetapis.com/",
       |      "refreshToken": "testrefreshtoken",
       |      "mcAccessToken": "testAccessToken",
       |      "mcAccessTokenExp": 3555
       |    },
       |    "organization": {
       |      "id": 8000000,
       |      "enterpriseId": 9000000,
       |      "dataContext": "enterprise",
       |      "stackKey": "S8",
       |      "region": "NA1"
       |    },
       |    "application": {
       |      "id": "appidxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
       |      "package": "apppacka-gexx-xxxx-xxxx-xxxxxxxxxxxx.xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
       |      "redirectUrl": "https://cool-mc-app.mce.salesforce.com/",
       |      "features": {},
       |      "userPermissions": []
       |    }
       |  }
       |}
       """.stripMargin

  override def mockJwt(): String = {
    encode(mockDecodedJwt)
  }

  override def decode(jwt: String): Try[(String, String, String)] = {
    //Return decoded result from the first key that match
    secretKeyCollection.collectFirst(
      //convert function to partial function since collectFirst needs it
      Function.unlift(key =>
        //Jwt.decodeRawAll returns a Try, transform that to Option for unlifting
        Jwt.decodeRawAll(jwt, key, Seq(JwtAlgorithm.HS256)).toOption)
    ) match {
        //Convert back to Try[T] to keep function signature intact
        case Some(o) => Success(o)
        case None    => Failure(new Exception("Unable to decode JWT with signing secret"))
      }
  }

  override def encode(jwtPayload: String): String = {
    //Encode with first key always; since encoding is not applicable to production and is only used
    //for dev login
    Jwt.encode(jwtPayload, secretKeyCollection.head, JwtAlgorithm.HS256)
  }

}
