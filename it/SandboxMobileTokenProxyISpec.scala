import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.parse
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import stubs.MobileAuthStub.ggSignInSuccess
import utils.{WireMockSupport, WsScalaTestClient}
import play.api.libs.ws.WSBodyWritables.*

class SandboxMobileTokenProxyISpec extends AnyWordSpecLike with Matchers with GuiceOneServerPerSuite with WsScalaTestClient with WireMockSupport {
  override implicit lazy val app: Application = appBuilder.build()

  def appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder().configure(
    Map("microservice.services.mobile-auth-stub.port" -> wireMockPort)
  )

  implicit lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val ws: AhcWSClient = AhcWSClient()(app.materializer)

  val mobileUserId: (String, String) = "X-MOBILE-USER-ID" -> "167927702220"

  "POST /mobile-token-proxy/oauth/token" should {
    def postOAuthToken(form: String): WSResponse = {
      val jsonHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"
      await(
        wsUrl(s"/mobile-token-proxy/oauth/token?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75")
          .addHttpHeaders(jsonHeader, mobileUserId)
          .post(parse(form))
      )
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
      val response = await(
        wsUrl("/mobile-token-proxy/oauth/token")
          .addHttpHeaders("Accept" -> "application/vnd.hmrc.1.0+json")
          .post(parse(formWithAuthCode))
      )
      response.status shouldBe 400
    }

    "return bad request if journeyId is invalid" in {
      val response = await(
        wsUrl("/mobile-token-proxy/oauth/token?journeyId=ThisIsAnInvalidJourneyId")
          .addHttpHeaders("Accept" -> "application/vnd.hmrc.1.0+json")
          .post(parse(formWithAuthCode))
      )
      response.status shouldBe 400
    }

  }

  "GET /mobile-token-proxy/oauth/authorize" should {
    "redirect to /gg/sign-in and receive a response" in {
      ggSignInSuccess()
      val call = await(
        wsUrl(s"/mobile-token-proxy/oauth/authorize?journeyId=dd1ebd2e-7156-47c7-842b-8308099c5e75")
          .addHttpHeaders(mobileUserId)
          .get()
      )
      call.status  shouldBe 200
      call.body.trim should include("Success code=sandboxSuccess")
    }
  }
}
