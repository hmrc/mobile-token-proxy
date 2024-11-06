/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.mobiletokenproxy.controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import eu.timepit.refined.auto._
import org.scalamock.matchers.MatcherBase
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, UNAUTHORIZED}
import play.api.libs.json.JsValue
import play.api.libs.json.Json.parse
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mobiletokenproxy.config.ProxyPassThroughHttpHeaders
import uk.gov.hmrc.mobiletokenproxy.model.TokenOauthResponse
import uk.gov.hmrc.mobiletokenproxy.services.TokenService
import uk.gov.hmrc.mobiletokenproxy.types.ModelTypes.JourneyId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MobileTokenProxySpec extends PlaySpec with Results with MockFactory with ScalaFutures {
  implicit val system:       ActorSystem  = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)

  private val journeyId:   JourneyId = "dd1ebd2e-7156-47c7-842b-8308099c5e75"
  private val tokenExpory: Long      = 14400

  private val service         = mock[TokenService]
  private val vendorHeader    = "X-Vendor-Instance-Id"
  private val deviceIdHeader  = "X-Client-Device-ID"
  private val userAgentHeader = "User-Agent"
  private val authCode        = "authCode123"
  private val refreshToken    = "refreshToken123"
  private val accessToken     = "495b5b1725d590eb87d0f6b7dcea32a9"

  private val testHTTPHeadersWithScrambledCase: Seq[(String, String)] =
    Seq(vendorHeader.toUpperCase() -> "header vendor", deviceIdHeader.toLowerCase() -> "header device Id")

  private val tokenResponseJson =
    s"""{"access_token":"$accessToken","refresh_token":"$refreshToken","expires_in":$tokenExpory}"""

  private val messagesActionBuilder: MessagesActionBuilder =
    new DefaultMessagesActionBuilderImpl(stubBodyParser[AnyContent](), stubMessagesApi())
  private val cc = stubControllerComponents()

  private val mcc: MessagesControllerComponents = DefaultMessagesControllerComponents(
    messagesActionBuilder,
    DefaultActionBuilder(stubBodyParser[AnyContent]()),
    cc.parsers,
    cc.messagesApi,
    cc.langs,
    cc.fileMimeTypes,
    ExecutionContext.global
  )

  val controller =
    new MobileTokenProxy(
      service,
      new ProxyPassThroughHttpHeaders(Seq(vendorHeader, deviceIdHeader)),
      responseType                 = "code",
      pathToAPIGatewayAuthService  = "http://localhost:8236/oauth/authorize",
      ngcClientId                  = "ngc-client-id",
      ngcScope                     = "ngc-some-scopes",
      ngcRedirectUri               = "ngc_redirect_uri",
      ngcClientIdV2                = "ngc-client-id-v2",
      ngcScopeV2                   = "ngc-some-scopes-v2",
      ngcRedirectUriV2             = "ngc_redirect_uri-v2",
      messagesControllerComponents = mcc
    )

  def headerCarrierWith(headers: Seq[(String, String)]): MatcherBase =
    argThat { (hc: HeaderCarrier) =>
      val allFound: Seq[Boolean] = headers.map { header =>
        hc.extraHeaders.contains(header)
      }
      !allFound.contains(false)
    }

  def requestWithJsonBody(body: String): FakeRequest[JsValue] =
    FakeRequest(POST, "url").withBody(parse(body)).withHeaders("Content-Type" -> "application/json")

  def requestWithHttpHeaders[T](fakeRequest: FakeRequest[T]): FakeRequest[T] =
    fakeRequest.withHeaders(testHTTPHeadersWithScrambledCase: _*)

  def requestWithFormEncodedBody(body: Map[String, Seq[String]]): FakeRequest[Map[String, Seq[String]]] =
    FakeRequest(POST, "url").withBody(body).withHeaders("Content-Type" -> "application/x-www-form-urlencoded")

  "token request payloads" should {
    val requestWithEmptyJsonBody: FakeRequest[JsValue] = requestWithJsonBody("{}")
    val requestWithBadJsonBody:   FakeRequest[JsValue] = requestWithJsonBody("""{"baddata":"error"}""")

    "return Error if invalid token  Request is send " in {
      val result = controller.token(journeyId)(requestWithBadJsonBody).futureValue
      result.header.status mustBe 400
    }

    "return BadRequest if the authorization code or refreshToken is missing" in {
      val result = controller.token(journeyId)(requestWithEmptyJsonBody).futureValue
      result.header.status mustBe 400
    }

    "return BadRequest if both authorization code and refreshToken is supplied" in {
      val result =
        controller
          .token(journeyId)(
            requestWithJsonBody(s"""{"authorizationCode":"123456789","refreshToken":"some refresh token"}""")
          )
          .futureValue
      result.header.status mustBe 400
    }
  }

  "requesting a new access-token/refresh-token from an access-code" should {
    val tokenRequestWithAuthCode = s"""{"authorizationCode":"$authCode"}"""
    lazy val jsonRequestWithAuthCode: FakeRequest[JsValue] = requestWithJsonBody(tokenRequestWithAuthCode)

    "successfully return access-token and refresh token for a valid request" in {
      (service
        .getTokenFromAccessCode(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(authCode, journeyId, false, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(Future.successful(TokenOauthResponse(accessToken, refreshToken, tokenExpory)))

      val result = controller.token(journeyId)(requestWithHttpHeaders(jsonRequestWithAuthCode))

      result.futureValue.header.status mustBe 200
      contentAsJson(result) mustBe parse(tokenResponseJson)
    }
    "Return Unauthorized error if getTokenFromAccessCode service failed with BAD RequestException" in {
      (service
        .getTokenFromAccessCode(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(authCode, journeyId, false, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(
          Future.failed(
            new BadRequestException(s"BAD Request Exception from APIGatewayTokenService: 400")
          )
        )

      val result = controller.token(journeyId)(requestWithHttpHeaders(jsonRequestWithAuthCode))

      result.futureValue.header.status mustBe 401
    }
    "Return Unauthorized error if getTokenFromAccessCode service failed with BAD Request" in {
      (service
        .getTokenFromAccessCode(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(authCode, journeyId, false, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(
          Future.failed(
            UpstreamErrorResponse(s"BAD Request from APIGatewayTokenService: 400", BAD_REQUEST, BAD_REQUEST, Map.empty)
          )
        )

      val result = controller.token(journeyId)(requestWithHttpHeaders(jsonRequestWithAuthCode))

      result.futureValue.header.status mustBe 401
    }
    "Return Unauthorized error if getTokenFromAccessCode service failed with Unauthorized Request" in {
      (service
        .getTokenFromAccessCode(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(authCode, journeyId, false, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(
          Future.failed(
            UpstreamErrorResponse(s"UNAUTHORIZED Request from APIGatewayTokenService: 401",
                                  UNAUTHORIZED,
                                  UNAUTHORIZED,
                                  Map.empty)
          )
        )

      val result = controller.token(journeyId)(requestWithHttpHeaders(jsonRequestWithAuthCode))

      result.futureValue.header.status mustBe 401

    }
    "Return FORBIDDEN error if getTokenFromAccessCode service failed with FORBIDDEN Request" in {
      (service
        .getTokenFromAccessCode(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(authCode, journeyId, false, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(
          Future.failed(
            UpstreamErrorResponse(s"FORBIDDEN Request from APIGatewayTokenService: 403",
                                  FORBIDDEN,
                                  FORBIDDEN,
                                  Map.empty)
          )
        )

      val result = controller.token(journeyId)(requestWithHttpHeaders(jsonRequestWithAuthCode))

      result.futureValue.header.status mustBe FORBIDDEN

    }
  }

  "requesting a new access-token from a refresh-token" should {
    val tokenRequestWithRefreshToken = s"""{"refreshToken":"$refreshToken"}"""
    val jsonRequestRequestWithRefreshToken: FakeRequest[JsValue] = requestWithJsonBody(tokenRequestWithRefreshToken)

    "successfully return access-token and refresh-token for a valid request + pass configured TxM HTTP headers to backend services " in {
      (service
        .getTokenFromRefreshToken(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(refreshToken, journeyId, false, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(Future.successful(TokenOauthResponse(accessToken, refreshToken, tokenExpory)))

      val result = controller.token(journeyId)(requestWithHttpHeaders(jsonRequestRequestWithRefreshToken))

      result.futureValue.header.status mustBe 200
      contentAsJson(result) mustBe parse(tokenResponseJson)
    }

    "Return Unauthorized error if getTokenFromRefreshToken service failed with BAd Request Exception" in {
      (service
        .getTokenFromRefreshToken(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(refreshToken, journeyId, false, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(
          Future.failed(
            new BadRequestException(s"BAD Request from APIGatewayTokenService: 400")
          )
        )

      val result = controller.token(journeyId)(requestWithHttpHeaders(jsonRequestRequestWithRefreshToken))

      result.futureValue.header.status mustBe UNAUTHORIZED

    }
    "Return Unauthorized error if getTokenFromRefreshToken service failed with BAd Request " in {
      (service
        .getTokenFromRefreshToken(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(refreshToken, journeyId, false, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(
          Future.failed(
            UpstreamErrorResponse(s"BAD Request from APIGatewayTokenService: 400", BAD_REQUEST, BAD_REQUEST, Map.empty)
          )
        )

      val result = controller.token(journeyId)(requestWithHttpHeaders(jsonRequestRequestWithRefreshToken))

      result.futureValue.header.status mustBe UNAUTHORIZED

    }
    "Return Unauthorized error if getTokenFromRefreshToken service failed with UNAUTHORIZED Request " in {
      (service
        .getTokenFromRefreshToken(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(refreshToken, journeyId, false, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(
          Future.failed(
            UpstreamErrorResponse(s"UNAUTHORIZED Request from APIGatewayTokenService: 401",
                                  UNAUTHORIZED,
                                  UNAUTHORIZED,
                                  Map.empty)
          )
        )

      val result = controller.token(journeyId)(requestWithHttpHeaders(jsonRequestRequestWithRefreshToken))

      result.futureValue.header.status mustBe UNAUTHORIZED

    }
    "Return FORBIDDEN error if getTokenFromRefreshToken service failed with FORBIDDEN Request " in {
      (service
        .getTokenFromRefreshToken(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(refreshToken, journeyId, false, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(
          Future.failed(
            UpstreamErrorResponse(s"FORBIDDEN Request from APIGatewayTokenService: 400",
                                  FORBIDDEN,
                                  FORBIDDEN,
                                  Map.empty)
          )
        )

      val result = controller.token(journeyId)(requestWithHttpHeaders(jsonRequestRequestWithRefreshToken))

      result.futureValue.header.status mustBe FORBIDDEN

    }

  }

  "token v2 request payloads" should {
    val requestWithEmptyFormEncodedBody: FakeRequest[Map[String, Seq[String]]] = requestWithFormEncodedBody(Map.empty)

    "return BadRequest if the authorization code or refreshToken is missing" in {
      val result = controller.tokenV2(journeyId)(requestWithEmptyFormEncodedBody).futureValue
      result.header.status mustBe 400
    }

    "return BadRequest if both authorization code and refreshToken is supplied" in {
      val result =
        controller
          .tokenV2(journeyId)(
            requestWithFormEncodedBody(Map("refreshToken" -> Seq("token"), "authorizationCode" -> Seq("authCode")))
          )
          .futureValue
      result.header.status mustBe 400
    }
  }

  "token v2 requesting a new access-token/refresh-token from an access-code" should {
    val tokenRequestWithAuthCode = Map("authorizationCode" -> Seq("authCode123"))
    lazy val formRequestWithAuthCode: FakeRequest[Map[String, Seq[String]]] =
      requestWithFormEncodedBody(tokenRequestWithAuthCode)

    "successfully return access-token and refresh token for a valid request" in {
      (service
        .getTokenFromAccessCode(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(authCode, journeyId, true, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(Future.successful(TokenOauthResponse(accessToken, refreshToken, tokenExpory)))

      val result = controller.tokenV2(journeyId)(requestWithHttpHeaders(formRequestWithAuthCode))

      result.futureValue.header.status mustBe 200
      contentAsJson(result) mustBe parse(tokenResponseJson)
    }
  }

  "token v2 requesting a new access-token from a refresh-token" should {
    val tokenRequestWithAuthCode = Map("refreshToken" -> Seq("refreshToken123"))
    lazy val formRequestWithAuthCode: FakeRequest[Map[String, Seq[String]]] =
      requestWithFormEncodedBody(tokenRequestWithAuthCode)

    "successfully return access-token and refresh-token for a valid request + pass configured TxM HTTP headers to backend services " in {
      (service
        .getTokenFromRefreshToken(_: String, _: JourneyId, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(refreshToken, journeyId, true, headerCarrierWith(testHTTPHeadersWithScrambledCase), *)
        .returning(Future.successful(TokenOauthResponse(accessToken, refreshToken, tokenExpory)))

      val result = controller.tokenV2(journeyId)(requestWithHttpHeaders(formRequestWithAuthCode))

      result.futureValue.header.status mustBe 200
      contentAsJson(result) mustBe parse(tokenResponseJson)
    }
  }

  "requesting the authorize service" should {
    "return a 303 redirect with the URL to the API Gateway authorize service" in {
      val result = controller.authorize(journeyId)(requestWithHttpHeaders(FakeRequest()))

      result.futureValue.header.status mustBe 303
      header("Location", result).get mustBe
      "http://localhost:8236/oauth/authorize?client_id=ngc-client-id&redirect_uri=ngc_redirect_uri&scope=ngc-some-scopes&response_type=code"
      header(vendorHeader, result).get mustBe "header vendor"
      header(deviceIdHeader, result).get mustBe "header device Id"
    }
    "return a 303 redirect with the URL to the API Gateway authorize service when serviceId = ngc" in {
      val result = controller.authorize(journeyId, "ngc")(requestWithHttpHeaders(FakeRequest()))

      result.futureValue.header.status mustBe 303
      header("Location", result).get mustBe
      "http://localhost:8236/oauth/authorize?client_id=ngc-client-id&redirect_uri=ngc_redirect_uri&scope=ngc-some-scopes&response_type=code"
      header(vendorHeader, result).get mustBe "header vendor"
      header(deviceIdHeader, result).get mustBe "header device Id"
    }
    "return throw exception when serviceId = Invalid" in {
      intercept[IllegalArgumentException] {
        controller.authorize(journeyId, "invalid")(requestWithHttpHeaders(FakeRequest()))
      }
    }
  }

  "requesting the V2 authorize service" should {
    "return a 303 redirect with the new URL and user-agent header to the API Gateway authorize service" in {
      val result = controller.authorizeV2(journeyId, "userAgentHeader")(requestWithHttpHeaders(FakeRequest()))

      result.futureValue.header.status mustBe 303
      header("Location", result).get mustBe
      "http://localhost:8236/oauth/authorize?client_id=ngc-client-id-v2&redirect_uri=ngc_redirect_uri-v2&scope=ngc-some-scopes-v2&response_type=code"
      header(vendorHeader, result).get mustBe "header vendor"
      header(deviceIdHeader, result).get mustBe "header device Id"
      header(userAgentHeader, result).get mustBe "userAgentHeader"
    }
  }
}
