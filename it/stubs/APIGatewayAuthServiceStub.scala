package stubs

import com.github.tomakehurst.wiremock.client.WireMock._

object APIGatewayAuthServiceStub {
  def oauthRedirectSuccess(): Unit = {
    val redirectUrl =
      "/oauth/authorize?client_id=i_whTXqBWq9xj0BqdtJ4b_YaxV8a&" +
        "redirect_uri=urn:ietf:wg:oauth:2.0:oob:auto&" +
        "scope=read:personal-income+read:customer-profile+read:messages+read:submission-tracker+read:web-session+read:native-apps-api-orchestration+read:mobile-tax-credits-summary&" +
        "response_type=code"

    stubFor(get(urlEqualTo(redirectUrl)).willReturn(aResponse().withStatus(200)))
  }

  def oauthTokenExchangeSuccess(): Unit = {
    val response = """{ "access_token": "accessToken", "refresh_token": "refreshToken", "expires_in": 3600 }"""

    stubFor(post(urlPathEqualTo("/oauth/token"))
      .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(response)))
  }
}
