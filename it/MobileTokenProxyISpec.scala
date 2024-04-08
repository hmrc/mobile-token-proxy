import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.parse
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import stubs.APIGatewayAuthServiceStub._
import utils.{WireMockSupport, WsScalaTestClient}

class MobileTokenProxyISpec
    extends AnyWordSpecLike
    with Matchers
    with GuiceOneServerPerSuite
    with WsScalaTestClient
    with WireMockSupport {

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
      "api-gateway.ngc.v2.client_id"             -> "i_whTXqBWq9xj0BqdtJ4b_YaxV8a",
      "api-gateway.ngc.v2.redirect_uri"          -> "uk.gov.hmrc://hmrcapp",
      "api-gateway.ngc.v2.scope"                 -> "read:personal-income+read:customer-profile+read:messages+read:submission-tracker+read:web-session+read:native-apps-api-orchestration+read:mobile-tax-credits-summary",
      "api-gateway.expiry_decrement"             -> 0,
      "auditing.enabled"                         -> false
    )

  implicit lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val ws: AhcWSClient = AhcWSClient()(app.materializer)
  val authUserId = "/userId"

  "GET /ping/ping" should {
    "be healthy" in {
      val response = await(wsUrl("/ping/ping").get())
      response.status shouldBe 200
    }
  }

  "GET /mobile-token-proxy/oauth/invalidService" should {
    "return an invalid service error" in {
      val response = await(
        wsUrl(
          s"/mobile-token-proxy/oauth/invalidService/authorize?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75"
        ).get()
      )
      response.status shouldBe 500
    }
  }

  val test = Table(
    ("urlPrefix"),
    ("/mobile-token-proxy/oauth/"),
    ("/mobile-token-proxy/oauth/ngc/")
  )

  forAll(test) { (urlPrefix: String) =>
    s"GET ${urlPrefix}authorize" should {
      "redirect to oauth successfully" in {
        oauthRedirectSuccess("urn:ietf:wg:oauth:2.0:oob:auto")
        val response = await(wsUrl(s"${urlPrefix}authorize?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75").get())
        response.status shouldBe 200
      }
    }

    s"POST ${urlPrefix}token" should {
      def postOAuthToken(form: String): WSResponse = {
        val jsonHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"
        await(
          wsUrl(s"${urlPrefix}token?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75")
            .addHttpHeaders(jsonHeader)
            .post(parse(form))
        )
      }

      def verifyPostOAuthTokenFailureStatusCode(
        form:                 String,
        upstreamResponseCode: Int,
        responseCodeToReport: Int
      ): Unit = {
        oauthTokenExchangeFailure(upstreamResponseCode)

        val jsonHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"
        val response =
          await(
            wsUrl(s"${urlPrefix}token?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75")
              .addHttpHeaders(jsonHeader)
              .post(parse(form))
          )
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
        val response = await(
          wsUrl(s"${urlPrefix}token")
            .addHttpHeaders("Accept" -> "application/vnd.hmrc.1.0+json")
            .post(parse(formWithAuthCode))
        )
        response.status shouldBe 400
      }

      "return bad request if journeyId is invalid" in {
        val response = await(
          wsUrl(s"${urlPrefix}token?journeyId=ThisIsAnInvalidJourneyId")
            .addHttpHeaders("Accept" -> "application/vnd.hmrc.1.0+json")
            .post(parse(formWithAuthCode))
        )
        response.status shouldBe 400
      }
    }
  }
  s"GET /mobile-token-proxy/oauth/authorize/v2" should {
    "redirect to oauth successfully with new redirect URL and User-Agent header" in {
      oauthRedirectSuccess("uk.gov.hmrc://hmrcapp")
      val response = await(
        wsUrl(
          "/mobile-token-proxy/oauth/authorize/v2?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75&userAgent=user-agent"
        ).get()
      )
      response.status shouldBe 200
      println("HEADERS = " + response.headers)
    }
  }
}
