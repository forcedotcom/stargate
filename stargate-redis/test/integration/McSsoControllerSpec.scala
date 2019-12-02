package integration

import javax.inject.Inject

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.test.Helpers._
import play.api.test._
import pdi.jwt.{Jwt, JwtAlgorithm}
import play.api.Configuration
import play.api.libs.json.{JsDefined, JsString}
import play.api.mvc.{ControllerComponents, Session}

import com.salesforce.mce.stargate.controllers.McSsoController
import com.salesforce.mce.stargate.models.McSsoDecodedJwt
import com.salesforce.mce.stargate.services.SessionTrackingService
import com.salesforce.mce.stargate.services.impl.SessionTrackingServiceRedisImpl
import com.salesforce.mce.stargate.utils.{JedisConnection, JwtUtil, JwtUtilImpl}
import play.api.http.{SecretConfiguration, SessionConfiguration}
import play.api.libs.crypto.{CSRFTokenSignerProvider, DefaultCookieSigner}
import play.filters.csrf.{CSRFAddToken, CSRFConfig}

class McSsoControllerSpec extends PlaySpec with GuiceOneAppPerTest with BeforeAndAfterEach {
  val config = Configuration(ConfigFactory.load())

  override protected def beforeEach(): Unit = {
    JedisConnection.flushDB()
  }

  // Return a more-or-less working CSRF component
  protected def fakeCSRF(config: Configuration) =
    CSRFAddToken(
      CSRFConfig.fromConfiguration(config),
      new CSRFTokenSignerProvider(
        new DefaultCookieSigner(SecretConfiguration())
      ).get,
      SessionConfiguration()
    )

  "sso/mc/login" should {
    val request = FakeRequest(POST, "/sso/mc/login")
    val secretKey = "changeme"
    val jwtPayload = new JwtUtilImpl(Configuration(ConfigFactory.load())).mockDecodedJwt

    "be a 303 with JWT" in {
      val jwt = Jwt.encode(jwtPayload, secretKey, JwtAlgorithm.HS256)
      val response = route(app, request.withFormUrlEncodedBody(("jwt", jwt))).get
      status(response) mustBe SEE_OTHER
    }

    "redirect to redirectUrl in JWT" in {
      val jwt = Jwt.encode(jwtPayload, secretKey, JwtAlgorithm.HS256)
      val response = route(app, request.withFormUrlEncodedBody(("jwt", jwt))).get
      redirectLocation(response).get mustBe "https://cool-mc-app.mce.salesforce.com/"
    }

    "create session data with JWT" in {
      val jwt = Jwt.encode(jwtPayload, secretKey, JwtAlgorithm.HS256)
      val response = route(app, request.withFormUrlEncodedBody(("jwt", jwt))).get

      val sid = session(response).get("id").getOrElse(fail("missing session id"))

      val sessionTrackingService = new SessionTrackingServiceRedisImpl(config)
      Await.result(sessionTrackingService.checkAndUpdateTimeout(8000000, 1111118, sid), 2.seconds) must equal(true)

      session(response).get("userId").get mustBe "1111118"
      session(response).get("mid").get mustBe "8000000"
      session(response).get("eid").get mustBe "9000000"
    }

    "be a 400 without JWT" in {
      val response = route(app, request).get
      status(response) mustBe BAD_REQUEST
    }

    "do pre-login forbidden check callback" in {
      val jsonErrorMessage = "Organization has not been enabled"

      class AppMcSsoController @Inject() (
        cc: ControllerComponents,
        jwtUtil: JwtUtil,
        sessionTrackingService: SessionTrackingService,
        config: Configuration,
        addToken: CSRFAddToken,
      ) extends McSsoController(cc, jwtUtil, sessionTrackingService, config, addToken) {

        // simulate the case where user's organization has not been enabled
        override protected def preLoginForbiddenCheckCallback(userId: Long, mid: Long): Future[(Boolean, Option[String])] = Future.successful((false, Option(jsonErrorMessage)))

      }

      val appController = new AppMcSsoController(
        Helpers.stubControllerComponents(),
        new JwtUtilImpl(config),
        new SessionTrackingServiceRedisImpl(config),
        config,
        fakeCSRF(config)
      )
      val jwt = Jwt.encode(jwtPayload, secretKey, JwtAlgorithm.HS256)
      val response = appController.login.apply(request.withFormUrlEncodedBody(("jwt", jwt)))

      status(response) mustBe FORBIDDEN
      (contentAsJson(response) \ "error") must equal(JsDefined(JsString(jsonErrorMessage)))
    }

    "do post login callback" in {
      class AppMcSsoController @Inject() (
        cc: ControllerComponents,
        jwtUtil: JwtUtil,
        sessionTrackingService: SessionTrackingService,
        config: Configuration,
        addToken: CSRFAddToken
      ) extends McSsoController(cc, jwtUtil, sessionTrackingService, config, addToken) {

        override protected def postLoginCallback(session: Session, mcSsoDecodedJwt: McSsoDecodedJwt): Future[(Session, String)] = {
          val sessionPostLogin = Session(session.data ++ Map("meme" -> "i see what you did there"))
          val redirectUrlPostLogin = "https://www.reddit.com/r/corgi/"
          return Future.successful((sessionPostLogin, redirectUrlPostLogin))
        }

      }

      val appController = new AppMcSsoController(
        Helpers.stubControllerComponents(),
        new JwtUtilImpl(config),
        new SessionTrackingServiceRedisImpl(config),
        config,
        fakeCSRF(config)
      )
      val jwt = Jwt.encode(jwtPayload, secretKey, JwtAlgorithm.HS256)
      val response = appController.login.apply(request.withFormUrlEncodedBody(("jwt", jwt)))

      status(response) mustBe SEE_OTHER

      session(response).get("meme").get mustBe "i see what you did there"
      session(response).get("id").get mustNot be(None)
      session(response).get("userId").get mustBe "1111118"
      session(response).get("mid").get mustBe "8000000"
      session(response).get("eid").get mustBe "9000000"

      // Assert that we're getting a cookie for csrf
      val cookieName = config.get[String]("play.filters.csrf.cookie.name")
      cookies(response).get(cookieName).get mustNot be(None)
      cookies(response).get(cookieName).get.value must not be(null)
      cookies(response).get(cookieName).get.value.isEmpty must be(false)

      redirectLocation(response).get mustBe "https://www.reddit.com/r/corgi/"
    }

  }

  "sso/mc/logout" should {
    val request = FakeRequest(GET, "/sso/mc/logout").withSession("userId" -> "1")

    "be a 200" in {
      val response = route(app, request).get
      status(response) mustBe OK
    }

    "reset session" in {
      val sessionTrackingService = new SessionTrackingServiceRedisImpl(config)
      val sid = Await.result(sessionTrackingService.create(8000000, 1111118), 2.seconds).getOrElse(fail("missing session id"))
      val request = FakeRequest(GET, "/sso/mc/logout").withSession(
        "id" -> sid, "userId" -> "1111118", "mid" -> "8000000"
      )

      val response = route(app, request).get
      session(response).isEmpty mustBe true
      Await.result(sessionTrackingService.checkAndUpdateTimeout(8000000, 1111118, sid), 2.seconds) mustBe false
    }

  }

}
