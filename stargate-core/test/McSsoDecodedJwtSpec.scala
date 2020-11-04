package integration

import play.api.libs.json.Json
import play.api.Configuration

import com.typesafe.config.ConfigFactory
import org.scalatestplus.play._

import com.salesforce.mce.stargate.models.McSsoDecodedJwt
import com.salesforce.mce.stargate.utils.JwtUtilImpl

class McSsoDecodedJwtSpec extends PlaySpec {

  "McSsoDecodedJwt with mcAccessToken in payload" should {
    val jwtPayload = new JwtUtilImpl(Configuration(ConfigFactory.load())).mockDecodedJwt(true)

    val jsonFormat = Json.parse(jwtPayload)
    val decodedJwt = Json.fromJson[McSsoDecodedJwt](jsonFormat).get

    "have a mcAccessToken and mcAccessTokenExp set " in {
      decodedJwt.request.rest.mcAccessToken.get mustBe "testAccessToken"
      decodedJwt.request.rest.mcAccessTokenExp.get mustBe 3555
    }
  }

  "McSsoDecodedJwt without mcAccessToken in payload" should {
    val jwtPayload = new JwtUtilImpl(Configuration(ConfigFactory.load())).mockDecodedJwt(false)

    val jsonFormat = Json.parse(jwtPayload)
    val decodedJwt = Json.fromJson[McSsoDecodedJwt](jsonFormat).get

    "have a mcAccessToken and mcAccessTokenExp set to None" in {
      decodedJwt.request.rest.mcAccessToken mustBe None
      decodedJwt.request.rest.mcAccessTokenExp mustBe None
    }
  }
}
