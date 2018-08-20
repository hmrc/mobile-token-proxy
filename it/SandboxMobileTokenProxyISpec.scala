import org.scalatestplus.play.OneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.parse
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.play.test.UnitSpec
import utils.{WireMockSupport, WsScalaTestClient}
import stubs.MobileAuthStub.ggSignInSuccess

class SandboxMobileTokenProxyISpec extends UnitSpec
  with OneServerPerSuite with WsScalaTestClient with WireMockSupport {
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
      await(wsUrl(s"/mobile-token-proxy/oauth/token").withHeaders(jsonHeader, mobileUserId).post(parse(form)))
    }

    val formWithAuthCode: String = """{ "authorizationCode":"123"}"""

    "get token from authorizationCode" in {
      await(postOAuthToken(formWithAuthCode)).status shouldBe 200
    }

    "get token from refreshToken" in {
      await(postOAuthToken("""{ "refreshToken":"456"}""")).status shouldBe 200
    }

    "return bad request if both tokens are supplied" in {
      await(postOAuthToken("""{ "authorizationCode":"123", "refreshToken": "456"}""")).status shouldBe 400
    }

    "return bad request if neither token is supplied" in {
      await(postOAuthToken("{}")).status shouldBe 400
    }

  }

  "GET /mobile-token-proxy/oauth/authorize" should {
    "redirect to /gg/sign-in and receive a response" in {
      ggSignInSuccess()
      val call = await(wsUrl(s"/mobile-token-proxy/oauth/authorize").withHeaders(mobileUserId).get())
      call.status shouldBe 200
      call.body should include("Success code=sandboxSuccess")
    }
  }
}
