/*
 * Copyright 2019 HM Revenue & Customs
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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.google.inject.Provider
import org.scalamock.matchers.MatcherBase
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers
import play.api.libs.json.JsValue
import play.api.libs.json.Json.parse
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, header, stubBodyParser, stubControllerComponents, stubMessagesApi}
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletokenproxy.config.ProxyPassThroughHttpHeaders
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.model.TokenOauthResponse
import uk.gov.hmrc.mobiletokenproxy.services.TokenService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

class MobileTokenProxySpec extends UnitSpec with MockFactory with Matchers {
  implicit val system      : ActorSystem       = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val service       = mock[TokenService]
  private val cryptographer = mock[CompositeSymmetricCrypto]

  private val vendorHeader      = "X-Vendor-Instance-Id"
  private val deviceIdHeader    = "X-Client-Device-ID"
  private val journeyId         = Some("journeyId")
  private val authCode          = "authCode123"
  private val refreshToken      = "refreshToken123"
  private val accessToken       = "495b5b1725d590eb87d0f6b7dcea32a9"
  private val tokenExpory: Long = 14400

  private val testHTTPHeadersWithScrambledCase: Seq[(String, String)] =
    Seq(vendorHeader.toUpperCase() -> "header vendor", deviceIdHeader.toLowerCase() -> "header device Id")

  private val tokenResponseJson =
    s"""{"access_token":"$accessToken","refresh_token":"$refreshToken","expires_in":$tokenExpory}"""

  private val messagesActionBuilder: MessagesActionBuilder = new DefaultMessagesActionBuilderImpl(stubBodyParser[AnyContent](), stubMessagesApi())
  private val cc                                           = stubControllerComponents()

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
      mock[GenericConnector],
      service,
      new Provider[CompositeSymmetricCrypto]() {
        override def get(): CompositeSymmetricCrypto = cryptographer
      },
      new ProxyPassThroughHttpHeaders(Seq(vendorHeader, deviceIdHeader)),
      "http://localhost:8236/oauth/authorize",
      "client_id",
      "redirect_uri",
      "some-scopes",
      "code",
      mcc)

  def headerCarrierWith(headers: Seq[(String, String)]): MatcherBase = {
    argThat(
      (hc: HeaderCarrier) => {
        val allFound: Seq[Boolean] = headers.map { header =>
          hc.extraHeaders.contains(header)
        }
        !allFound.contains(false)
      })
  }

  def requestWithJsonBody(body: String): FakeRequest[JsValue] =
    FakeRequest(POST, "url").withBody(parse(body)).withHeaders("Content-Type" -> "application/json")

  def requestWithHttpHeaders[T](fakeRequest: FakeRequest[T]): FakeRequest[T] =
    fakeRequest.withHeaders(testHTTPHeadersWithScrambledCase: _*)


  "token request payloads" should {
    val requestWithEmptyJsonBody: FakeRequest[JsValue] = requestWithJsonBody("{}")

    "return BadRequest if the authorization code or refreshToken is missing" in {
      val result = await(controller.token()(requestWithEmptyJsonBody))
      status(result) shouldBe 400
    }

    "return BadRequest if the request is empty" in {
      val result = await(controller.token()(requestWithEmptyJsonBody))
      status(result) shouldBe 400
    }

    "return BadRequest if both authorization code and refreshToken is supplied" in {
      val result = await(
        controller.token()(
          requestWithJsonBody(s"""{"authorizationCode":"123456789","refreshToken":"some refresh token"}""")))
      status(result) shouldBe 400
    }
  }

  "requesting a new access-token/refresh-token from an access-code" should {
    val tokenRequestWithAuthCode = s"""{"authorizationCode":"$authCode"}"""
    lazy val jsonRequestWithAuthCode: FakeRequest[JsValue] = requestWithJsonBody(tokenRequestWithAuthCode)

    "successfully return access-token and refresh token for a valid request" in {
      (service.getTokenFromAccessCode(_: String, _: Option[String])(_: HeaderCarrier, _: ExecutionContext)).expects(
        authCode, journeyId, headerCarrierWith(testHTTPHeadersWithScrambledCase), *).returning(
        TokenOauthResponse(accessToken, refreshToken, tokenExpory))

      val result = await(controller.token(journeyId)(requestWithHttpHeaders(jsonRequestWithAuthCode)))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe parse(tokenResponseJson)
    }
  }

  "requesting a new access-token from a refresh-token" should {
    val tokenRequestWithRefreshToken =s"""{"refreshToken":"$refreshToken"}"""
    val jsonRequestRequestWithRefreshToken: FakeRequest[JsValue] = requestWithJsonBody(tokenRequestWithRefreshToken)

    "successfully return access-token and refresh-token for a valid request + pass configured TxM HTTP headers to backend services " in {
      (service.getTokenFromRefreshToken(_: String, _: Option[String])(_: HeaderCarrier, _: ExecutionContext)).expects(
        refreshToken, journeyId, headerCarrierWith(testHTTPHeadersWithScrambledCase), *).returning(
        TokenOauthResponse(accessToken, refreshToken, tokenExpory))

      val result = await(controller.token(journeyId)(requestWithHttpHeaders(jsonRequestRequestWithRefreshToken)))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe parse(tokenResponseJson)
    }
  }

  "requesting the authorize service" should {
    "return a 303 redirect with the URL to the API Gateway authorize service" in {
      implicit val timeout: Timeout = new Timeout(FiniteDuration(10, "seconds"))

      val result = await(controller.authorize()(requestWithHttpHeaders(FakeRequest())))

      status(result) shouldBe 303
      header("Location", result).get shouldBe
        "http://localhost:8236/oauth/authorize?client_id=client_id&redirect_uri=redirect_uri&scope=some-scopes&response_type=code"
      header(vendorHeader, result).get shouldBe "header vendor"
      header(deviceIdHeader, result).get shouldBe "header device Id"
    }
  }
}

