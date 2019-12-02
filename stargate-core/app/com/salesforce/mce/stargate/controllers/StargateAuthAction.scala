/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.stargate.controllers

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.Json
import play.api.mvc.Results.Unauthorized
import play.api.mvc._

import com.salesforce.mce.stargate.services.SessionTrackingService

class StargateAuthAction @Inject() (
  sessionTrackingService: SessionTrackingService,
  defaultParser: BodyParsers.Default
)(implicit ec: ExecutionContext)
  extends ActionBuilderImpl(defaultParser) {

  val requiredKeys = Seq("id", "userId", "mid", "eid")

  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
    if (!requiredKeys.forall(request.session.data.contains(_))) return unauthorizedFuture

    val sessionData = request.session.data.filterKeys(Set("id", "userId", "mid"))
    sessionTrackingService.checkAndUpdateTimeout(
      sessionData.get("mid").get.toLong,
      sessionData.get("userId").get.toLong,
      sessionData.get("id").get
    ).flatMap {
        // invoke action and set new session for the result to extend cookie expiration time
        if (_) block(request).map(result => result.withSession(result.session(request))) else unauthorizedFuture
      }
  }

  private def unauthorizedFuture: Future[Result] = Future.successful(Unauthorized(
    Json.obj("error" -> "session not authenticated!")
  ))
}
