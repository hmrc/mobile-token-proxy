import org.scalatestplus.play.OneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.parse
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSClient, WSResponse}
import stubs.APIGatewayAuthServiceStub._
import uk.gov.hmrc.play.test.UnitSpec
import utils.{WireMockSupport, WsScalaTestClient}

class MobileTokenProxyISpec extends UnitSpec
  with OneServerPerSuite with WsScalaTestClient with WireMockSupport{
  override implicit lazy val app: Application = appBuilder.build()

  def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder().configure(
      "api-gateway.pathToAPIGatewayAuthService" -> s"http://localhost:$wireMockPort/oauth/authorize",
      "api-gateway.pathToAPIGatewayTokenService" -> s"http://localhost:$wireMockPort/oauth/token",
      "api-gateway.scope" -> "read:personal-income+read:customer-profile+read:messages+read:submission-tracker+read:web-session+read:native-apps-api-orchestration+read:mobile-tax-credits-summary",
      "api-gateway.response_type" -> "code",
      "api-gateway.client_id" -> "i_whTXqBWq9xj0BqdtJ4b_YaxV8a",
      "api-gateway.redirect_uri" -> "urn:ietf:wg:oauth:2.0:oob:auto",
      "api-gateway.client_secret" -> "client_secret",
      "api-gateway.tax_calc_server_token" -> "tax_calc_server_token",
      "api-gateway.expiry_decrement"  -> 0
    )

  implicit lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val ws = AhcWSClient()(app.materializer)
  val authUserId = "/userId"

  "GET /ping/ping" should {
    "be healthy" in {
      val response = await(wsUrl("/ping/ping").get())
      response.status shouldBe 200
    }
  }

  "GET /mobile-token-proxy/oauth/authorize" should {
    "redirect to oauth successfully" in {
      oauthRedirectSuccess()
      val response = await(wsUrl("/mobile-token-proxy/oauth/authorize").get())
      response.status shouldBe 200
    }
  }

  "GET /mobile-token-proxy/oauth/taxcalctoken" should {
    "return a token" in {
      val response = await(wsUrl("/mobile-token-proxy/oauth/taxcalctoken").get())
      response.status shouldBe 200
      response.json.toString shouldBe """{"token":"lRyIT7HZxuAJL66JeCgrFlL+tpMGdBYpa8G9plvRHG4="}"""
    }
  }

  "POST /mobile-token-proxy/oauth/token" should {
    def postOAuthToken(form: String): WSResponse = {
      val jsonHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"
      await(wsUrl(s"/mobile-token-proxy/oauth/token").withHeaders(jsonHeader).post(parse(form)))
    }

    def verifyPostOAuthTokenFailureStatusCode(form: String, upstreamResponseCode:Int, responseCodeToReport: Int): Unit = {
      oauthTokenExchangeFailure(upstreamResponseCode)

      val jsonHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"
      val response = await(wsUrl(s"/mobile-token-proxy/oauth/token").withHeaders(jsonHeader).post(parse(form)))
      response.status shouldBe responseCodeToReport
    }

    val formWithAuthCode: String = """{ "authorizationCode":"123"}"""

    "get token from authorizationCode" in {
      oauthTokenExchangeSuccess()
      val response = await(postOAuthToken(formWithAuthCode))
      response.status shouldBe 200
    }

    "get token from refreshToken" in {
      oauthTokenExchangeSuccess()
      val response = await(postOAuthToken("""{ "refreshToken":"456"}"""))
      response.status shouldBe 200
    }

    "return bad request if both tokens are supplied" in {
      val response = await(postOAuthToken("""{ "authorizationCode":"123", "refreshToken": "456"}"""))
      response.status shouldBe 400
    }

    "return bad request if neither token is supplied" in {
      val response = await(postOAuthToken("{}"))
      response.status shouldBe 400
    }

    "propagate error codes" in {
      verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 401, 401)
      verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 403, 403)
      verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 404, 500)
      verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 500, 500)
      verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 503, 500)
    }
  }
}
