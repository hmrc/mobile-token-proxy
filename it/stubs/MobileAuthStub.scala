package stubs

import com.github.tomakehurst.wiremock.client.WireMock._

object MobileAuthStub {

  def ggSignInSuccess(): Unit =
    stubFor(
      get(urlPathEqualTo("/gg/sign-in")).willReturn(aResponse().withStatus(200).withBody("Success code=sandboxSuccess"))
    )
}
