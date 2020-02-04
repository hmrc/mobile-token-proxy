import org.scalatest.{Matchers, WordSpecLike}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.parse
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSClient, WSResponse}
import stubs.MobileAuthStub.ggSignInSuccess
import uk.gov.hmrc.integration.ServiceSpec
import utils.{WireMockSupport, WsScalaTestClient}

class SandboxMobileTokenProxyISpec
    extends WordSpecLike
    with ServiceSpec
    with Matchers
    with GuiceOneServerPerSuite
    with WsScalaTestClient
    with WireMockSupport {
  override implicit lazy val app: Application = appBuilder.build()

  override def externalServices: Seq[String] = Seq()

  def appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder().configure(
    Map("microservice.services.mobile-auth-stub.port" -> wireMockPort)
  )

  implicit lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val ws: AhcWSClient = AhcWSClient()(app.materializer)

  val mobileUserId: (String, String) = "X-MOBILE-USER-ID" -> "167927702220"

  "POST /mobile-token-proxy/oauth/token" should {
    def postOAuthToken(form: String): WSResponse = {
      val jsonHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"
      wsUrl(s"/mobile-token-proxy/oauth/token?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75")
        .addHttpHeaders(jsonHeader, mobileUserId)
        .post(parse(form))
        .futureValue
    }

    val formWithAuthCode: String = """{ "authorizationCode":"123"}"""

    "get token from authorizationCode" in {
      postOAuthToken(formWithAuthCode).status shouldBe 200
    }

    "get token from refreshToken" in {
      postOAuthToken("""{ "refreshToken":"456"}""").status shouldBe 200
    }

    "return bad request if both tokens are supplied" in {
      postOAuthToken("""{ "authorizationCode":"123", "refreshToken": "456"}""").status shouldBe 400
    }

    "return bad request if neither token is supplied" in {
      postOAuthToken("{}").status shouldBe 400
    }

    "return bad request if journeyId is not supplied" in {
      val response = wsUrl("/mobile-token-proxy/oauth/token")
        .addHttpHeaders("Accept" -> "application/vnd.hmrc.1.0+json")
        .post(parse(formWithAuthCode))
        .futureValue
      response.status shouldBe 400
    }

    "return bad request if journeyId is invalid" in {
      val response = wsUrl("/mobile-token-proxy/oauth/token?journeyId=ThisIsAnInvalidJourneyId")
        .addHttpHeaders("Accept" -> "application/vnd.hmrc.1.0+json")
        .post(parse(formWithAuthCode))
        .futureValue
      response.status shouldBe 400
    }

  }

  "GET /mobile-token-proxy/oauth/authorize" should {
    "redirect to /gg/sign-in and receive a response" in {
      ggSignInSuccess()
      val call = wsUrl(s"/mobile-token-proxy/oauth/authorize?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75")
        .addHttpHeaders(mobileUserId)
        .get()
        .futureValue
      call.status shouldBe 200
      call.body   should include("Success code=sandboxSuccess")
    }
  }
}
