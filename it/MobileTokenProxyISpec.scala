import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.parse
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSClient, WSResponse}
import stubs.APIGatewayAuthServiceStub._
import uk.gov.hmrc.integration.ServiceSpec
import utils.{WireMockSupport, WsScalaTestClient}

class MobileTokenProxyISpec
    extends WordSpecLike
    with ServiceSpec
    with Matchers
    with GuiceOneServerPerSuite
    with WsScalaTestClient
    with WireMockSupport {

  override def externalServices: Seq[String] = Seq()

  override implicit lazy val app: Application = appBuilder.build()

  def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder().configure(
      "api-gateway.pathToAPIGatewayAuthService"  -> s"http://localhost:$wireMockPort/oauth/authorize",
      "api-gateway.pathToAPIGatewayTokenService" -> s"http://localhost:$wireMockPort/oauth/token",
      "api-gateway.response_type"                -> "code",
      "api-gateway.ngc.client_id"                -> "i_whTXqBWq9xj0BqdtJ4b_YaxV8a",
      "api-gateway.ngc.redirect_uri"             -> "urn:ietf:wg:oauth:2.0:oob:auto",
      "api-gateway.ngc.client_secret"            -> "client_secret",
      "api-gateway.ngc.scope"                    -> "read:personal-income+read:customer-profile+read:messages+read:submission-tracker+read:web-session+read:native-apps-api-orchestration+read:mobile-tax-credits-summary",
      "api-gateway.rds.client_id"                -> "rds_i_whTXqBWq9xj0BqdtJ4b_YaxV8a",
      "api-gateway.rds.redirect_uri"             -> "urn:ietf:wg:oauth:2.0:oob:auto",
      "api-gateway.rds.client_secret"            -> "rds_client_secret",
      "api-gateway.rds.scope"                    -> "read:mobile-rds-vehicle",
      "api-gateway.expiry_decrement"             -> 0
    )

  implicit lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val ws: AhcWSClient = AhcWSClient()(app.materializer)
  val authUserId = "/userId"

  "GET /ping/ping" should {
    "be healthy" in {
      val response = wsUrl("/ping/ping").get().futureValue
      response.status shouldBe 200
    }
  }

  val test = Table(
    ("Name of Group Of Tests", "urlPrefix", "serviceId"),
    ("Old Url with no Service Id", "/mobile-token-proxy/oauth/", "ngc"),
    ("New Url with NGC Service Id", "/mobile-token-proxy/oauth/ngc/", "ngc"),
    ("New Url with RDS Service Id", "/mobile-token-proxy/oauth/rds/", "rds")
  )

  forAll(test) { (name: String, urlPrefix: String, serviceId: String) =>
    s"GET ${urlPrefix}authorize" should {
      "redirect to oauth successfully" in {
        oauthRedirectSuccess(serviceId)
        val response = wsUrl(s"${urlPrefix}authorize?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75").get().futureValue
        response.status shouldBe 200
      }
    }

    s"POST ${urlPrefix}token" should {
      def postOAuthToken(form: String): WSResponse = {
        val jsonHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"
        wsUrl(s"${urlPrefix}token?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75")
          .addHttpHeaders(jsonHeader)
          .post(parse(form))
          .futureValue
      }

      def verifyPostOAuthTokenFailureStatusCode(
        form:                 String,
        upstreamResponseCode: Int,
        responseCodeToReport: Int
      ): Unit = {
        oauthTokenExchangeFailure(upstreamResponseCode)

        val jsonHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"
        val response =
          wsUrl(s"${urlPrefix}token?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75")
            .addHttpHeaders(jsonHeader)
            .post(parse(form))
            .futureValue
        response.status shouldBe responseCodeToReport
      }

      val formWithAuthCode: String = """{ "authorizationCode":"123"}"""

      "get token from authorizationCode" in {
        oauthTokenExchangeSuccess()
        val response = postOAuthToken(formWithAuthCode)
        response.status shouldBe 200
      }

      "get token from refreshToken" in {
        oauthTokenExchangeSuccess()
        val response = postOAuthToken("""{ "refreshToken":"456"}""")
        response.status shouldBe 200
      }

      "return bad request if both tokens are supplied" in {
        val response = postOAuthToken("""{ "authorizationCode":"123", "refreshToken": "456"}""")
        response.status shouldBe 400
      }

      "return bad request if neither token is supplied" in {
        val response = postOAuthToken("{}")
        response.status shouldBe 400
      }

      "propagate error codes" in {
        // 400 is returned by oauth-frontend in certain circumstances -  treat as 401
        verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 400, 401)
        verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 401, 401)
        verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 403, 403)
        verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 404, 500)
        verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 500, 500)
        verifyPostOAuthTokenFailureStatusCode(formWithAuthCode, 503, 500)
      }

      "return bad request if journeyId is not supplied" in {
        val response = wsUrl(s"${urlPrefix}token")
          .addHttpHeaders("Accept" -> "application/vnd.hmrc.1.0+json")
          .post(parse(formWithAuthCode))
          .futureValue
        response.status shouldBe 400
      }

      "return bad request if journeyId is invalid" in {
        val response = wsUrl(s"${urlPrefix}token?journeyId=ThisIsAnInvalidJourneyId")
          .addHttpHeaders("Accept" -> "application/vnd.hmrc.1.0+json")
          .post(parse(formWithAuthCode))
          .futureValue
        response.status shouldBe 400
      }
    }
  }
}
