/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.stargate.models

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/* Official Doc - Field Reference for Decoded JWT
   https://developer.salesforce.com/docs/atlas.en-us.noversion.mc-app-development.meta/mc-app-development/explanation-decoded-jwt.htm
 */
case class McSsoDecodedJwt(
  exp: Long,
  jti: String,
  request: McSsoJwtRequest
)

object McSsoDecodedJwt {
  implicit val reads: Reads[McSsoDecodedJwt] = (
    (JsPath \ "exp").read[Long] and
    (JsPath \ "jti").read[String] and
    (JsPath \ "request").read[McSsoJwtRequest]
  )(McSsoDecodedJwt.apply _)
}

case class McSsoJwtRequest(
  claimsVersion: Long,
  rest: McSsoJwtRequestRest,
  user: McSsoJwtRequestUser,
  organization: McSsoJwtRequestOrganization,
  application: McSsoJwtRequestApplication,
  query: Option[McSsoJwtRequestQuery]
)

object McSsoJwtRequest {
  implicit val reads: Reads[McSsoJwtRequest] = (
    (JsPath \ "claimsVersion").read[Long] and
    (JsPath \ "rest").read[McSsoJwtRequestRest] and
    (JsPath \ "user").read[McSsoJwtRequestUser] and
    (JsPath \ "organization").read[McSsoJwtRequestOrganization] and
    (JsPath \ "application").read[McSsoJwtRequestApplication] and
    (JsPath \ "query").readNullable[McSsoJwtRequestQuery]
  )(McSsoJwtRequest.apply _)
}

case class McSsoJwtRequestRest(
  authEndpoint: String,
  apiEndpointBase: String,
  refreshToken: String,
  mcAccessToken: Option[String] = None,
  mcAccessTokenExp: Option[Long] = None
)

object McSsoJwtRequestRest {
  implicit val reads: Reads[McSsoJwtRequestRest] = (
    (JsPath \ "authEndpoint").read[String] and
    (JsPath \ "apiEndpointBase").read[String] and
    (JsPath \ "refreshToken").read[String] and
    (JsPath \ "mcAccessToken").readNullable[String] and
    (JsPath \ "mcAccessTokenExp").readNullable[Long]
  )(McSsoJwtRequestRest.apply _)
}

case class McSsoJwtRequestUser(
  id: Long,
  email: String,
  culture: String,
  timezone: McSsoJwtRequestUserTimezone
)

object McSsoJwtRequestUser {
  implicit val reads: Reads[McSsoJwtRequestUser] = (
    (JsPath \ "id").read[Long] and
    (JsPath \ "email").read[String] and
    (JsPath \ "culture").read[String] and
    (JsPath \ "timezone").read[McSsoJwtRequestUserTimezone]
  )(McSsoJwtRequestUser.apply _)
}

case class McSsoJwtRequestUserTimezone(
  longName: String,
  shortName: String,
  offset: Double,
  dst: Boolean
)

object McSsoJwtRequestUserTimezone {
  implicit val reads: Reads[McSsoJwtRequestUserTimezone] = (
    (JsPath \ "longName").read[String] and
    (JsPath \ "shortName").read[String] and
    (JsPath \ "offset").read[Double] and
    (JsPath \ "dst").read[Boolean]
  )(McSsoJwtRequestUserTimezone.apply _)
}

case class McSsoJwtRequestOrganization(
  id: Long,
  enterpriseId: Long,
  dataContext: String,
  stackKey: String,
  region: String
)

object McSsoJwtRequestOrganization {
  implicit val reads: Reads[McSsoJwtRequestOrganization] = (
    (JsPath \ "id").read[Long] and
    (JsPath \ "enterpriseId").read[Long] and
    (JsPath \ "dataContext").read[String] and
    (JsPath \ "stackKey").read[String] and
    (JsPath \ "region").read[String]
  )(McSsoJwtRequestOrganization.apply _)
}

case class McSsoJwtRequestApplication(
  id: String,
  packageName: String, // "package" is keyword, so use "packageName" instead
  redirectUrl: String
) // "features" and "userPermissions" are excluded because no production data is available

object McSsoJwtRequestApplication {
  implicit val reads: Reads[McSsoJwtRequestApplication] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "package").read[String] and
    (JsPath \ "redirectUrl").read[String]
  )(McSsoJwtRequestApplication.apply _)
}

case class McSsoJwtRequestQuery(
  id: Option[String],
  deepLink: Option[String]
)

object McSsoJwtRequestQuery {
  implicit val reads: Reads[McSsoJwtRequestQuery] = (
    (JsPath \ "id").readNullable[String] and
    (JsPath \ "deepLink").readNullable[String]
  )(McSsoJwtRequestQuery.apply _)
}

