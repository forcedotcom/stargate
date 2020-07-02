/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.stargate.controllers

import javax.inject.{Inject, Singleton}

import scala.collection.immutable.Map
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc._
import play.api.{Configuration, Logger}
import play.filters.csrf.CSRFAddToken
import play.api.http.SessionConfiguration
import com.salesforce.mce.stargate.models.McSsoDecodedJwt
import com.salesforce.mce.stargate.services.SessionTrackingService
import com.salesforce.mce.stargate.utils.JwtUtil

@Singleton
class McSsoController @Inject() (
  cc: ControllerComponents,
  jwtUtil: JwtUtil,
  sessionTrackingService: SessionTrackingService,
  config: Configuration,
  addToken: CSRFAddToken,
  sessionConfig: SessionConfiguration
)(implicit ec: ExecutionContext) extends BaseController(cc) {

  val logger = Logger(this.getClass())
  val isDevLoginEnabled = config.get[Boolean]("com.salesforce.mce.stargate.isDevLoginEnabled")

  def login = addToken {
    Action.async { implicit request =>
      jwtFrom(request) match {
        case None => {
          log(false, request, Map("requestBody" -> request.body.asText.getOrElse("*empty*")))
          Future.successful(BadRequest)
        }
        case Some((header, claim, signature)) => processJwtClaimAndRedirect(claim, request)
      }
    }
  }

  def logout = Action.async { implicit request =>
    val data = for {
      mid <- request.session.data.get("mid")
      userId <- request.session.data.get("userId")
      id <- request.session.data.get("id")
    } yield (mid.toLong, userId.toLong, id)

    /*
    This is required due to a play framework bug which when Session is cleared using
    withNewSession or data map is empty in Session then the play_framework code
    applies DiscardingCookie which reset the PLAY_SESSION.
    But DiscardingCookie is not using sameSite settings. So the set-cookie is ignored by browser.
    For more info pl see the bug:
    https://github.com/playframework/playframework/issues/10122
     */
    val result = Ok.withCookies(Cookie(
      name = sessionConfig.cookieName,
      value = "",
      maxAge = Some(0),   // ensure cookie expires immediately, if not discarded
      secure = sessionConfig.secure,
      sameSite = sessionConfig.sameSite
    ))

    data.fold(Future.successful(result)) { case (mid, userId, id) =>
      sessionTrackingService.destroy(mid, userId, id).map(_ => result)
    }
  }

  def devLogin = Action.async { implicit request =>
    checkDevLoginEnabled { () =>
      val isLoggedIn = isSessionAvailable(request.session)
      Future.successful(Ok(com.salesforce.mce.stargate.views.html.McSso.devLogin(isLoggedIn, jwtUtil.mockDecodedJwt())))
    }
  }

  def devLoginSubmit = Action.async { implicit request =>
    checkDevLoginEnabled { () =>
      val d = request.body.asFormUrlEncoded.flatMap { data => data.get("jwt").map(_.head) }.get
      processJwtClaimAndRedirect(d, request)
    }
  }

  /** This callback method checks whether user is forbidden to access the application before session creation.
   *  In a Play app, this method could be overridden in a subclass whereby it checks on whether a user's
   *  organization (mid) is allowed to access the application.
   *  The default implementation always allow user to access.
   *
   *  @param userId Marketing Cloud User ID
   *  @param mid Marketing Cloud MID
   *  @return A future of a tuple of (boolean indicating user is authorized or not, optional forbidden error message)
   *         If user is authorized, Stargate proceeds with session creation.
   *         Otherwise, Stargate returns a 403 forbidden response.
   */
  protected def preLoginForbiddenCheckCallback(userId: Long, mid: Long): Future[(Boolean, Option[String])] =
    Future.successful((true, None))

  /** This is a callback method allows subclass to run arbitrary code upon successful user login.
   *  In a Play app, this method could be overridden in a subclass whereby it upserts user record into database
   *  or updates session data.
   *  The default implementation does nothing and simply returns a future of tuple of session and redirect URL
   *  per MC SSO JWT.
   *
   *  @param session HTTP session
   *  @param mcSsoDecodedJwt Decoded MC SSO JWT data
   *  @return A future of a tuple of (session, redirect URL)
   */
  protected def postLoginCallback(session: Session, mcSsoDecodedJwt: McSsoDecodedJwt): Future[(Session, String)] =
    Future.successful((session, mcSsoDecodedJwt.request.application.redirectUrl))

  private def checkDevLoginEnabled(func: () => Future[Result]): Future[Result] =
    if (isDevLoginEnabled) func() else Future.successful(Forbidden("Forbidden"))

  private def processJwtClaimAndRedirect(claim: String, request: Request[AnyContent]): Future[Result] = {

    /** Create new session in server-side (eg. in Redis) and save IDs in the JWT session for subsequent retrievals.
     *  If session creation is successful, postLoginCallback will be executed.
     *  Finally user will be redirected to the redirect URL.
     *
     *  @param mid Marketing Cloud MID
     *  @param userId Marketing Cloud User ID
     *  @param decodedJwt Decoded Marketing Cloud SSO JWT data
     *  @return A future of result
     */
    def createSessionAndResponse(mid: Long, userId: Long, decodedJwt: McSsoDecodedJwt): Future[Result] = {
      // create server-side session
      sessionTrackingService.create(mid, userId).flatMap { sidOpt =>
        sidOpt.map { sid =>
          // save IDs in JWT session so that subsequent requests in the same session could retrieve these IDs
          val newSession = Session(request.session.data ++ Seq(
            "id" -> sid, // session ID, eg. 2d8643f3-e4c3-47b0-a66c-f9af1af6b21a
            "userId" -> userId.toString,
            "mid" -> mid.toString,
            "eid" -> decodedJwt.request.organization.enterpriseId.toString
          ))
          postLoginCallback(newSession, decodedJwt).map { case (newSessionPostCallback, redirectUrlPostCallback) =>
            val userInfoForLogging = newSessionPostCallback.data.filterKeys(
              Set("id", "userId", "mid", "eid")
            )
            log(true, request, userInfoForLogging)
            Redirect(redirectUrlPostCallback).withSession(newSessionPostCallback)
          }
        }.getOrElse(Future.successful(InternalServerError(Json.obj("error" -> "Unable to create session"))))
      }
    }

    Json.parse(claim).validate[McSsoDecodedJwt] match {
      case JsSuccess(decodedJwt, _) =>
        val userId = decodedJwt.request.user.id
        val mid = decodedJwt.request.organization.id
        preLoginForbiddenCheckCallback(userId, mid).flatMap { case (isAuthorized, messageOpt) =>
          if (isAuthorized) {
            createSessionAndResponse(mid, userId, decodedJwt)
          } else {
            val errorMessage = messageOpt.getOrElse("Access forbidden")
            Future.successful(Forbidden(Json.obj("error" -> errorMessage)))
          }
        }
      case e: JsError =>
        log(false, request, Map("requestBody" -> request.body.asText.getOrElse("*empty*")))
        logger.error(JsError.toJson(e).toString)
        Future.successful(BadRequest(Json.obj("error" -> "Invalid JSON data")))
    }
  }

  private def jwtFrom(request: Request[AnyContent]): Option[(String, String, String)] =
    request.body.asFormUrlEncoded.flatMap { data =>
      data.get("jwt").map(_.head)
    }.flatMap { jwt => jwtUtil.decode(jwt).toOption }

  private def log(isSuccessful: Boolean, request: Request[AnyContent], data: Map[String, String]): Unit = {
    val message = if (isSuccessful) "Successful Authentication" else "Failed Authentication"
    val ipAddress = request.remoteAddress
    val userAgent = request.headers.get("User-Agent").getOrElse("unknown")
    val additionalData = data.map { case (k, v) => s"$k=$v" }.mkString(" ")
    logger.info(s"[$message] IP=$ipAddress UA=$userAgent $additionalData")
  }

}
