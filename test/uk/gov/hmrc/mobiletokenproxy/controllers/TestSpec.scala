/*
 * Copyright 2018 HM Revenue & Customs
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
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.crypto.{Crypted, CryptoWithKeysFromConfig}
import uk.gov.hmrc.http.{Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.mobiletokenproxy.model.TokenResponse
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class TestSpec extends UnitSpec with WithFakeApplication with ScalaFutures with BeforeAndAfterEach {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "token request payloads" should {

    "return BadRequest if the authorization code or refreshToken is missing" in new SuccessAccessCode {
      val result = await(controller.token()(jsonRequestEmpty))

      status(result) shouldBe 400
    }

    "return BadRequest if the request is empty" in new SuccessAccessCode {
      val result = await(controller.token()(jsonRequestEmpty))

      status(result) shouldBe 400
    }

    "return BadRequest if both authorization code and refreshToken is supplied" in new SuccessAccessCode {
      val result = await(controller.token()(jsonRequestWithAuthCodeAndRefreshToken))

      status(result) shouldBe 400
    }
  }

  "requesting a new access-token/refresh-token from an access-code" should {

    "successfully return access-token and refresh token for a valid request " in new SuccessAccessCode {
      val result = await(controller.token(Some("12345"))(addTestHeaders(jsonRequestWithAuthCode)))

      status(result) shouldBe 200
      controller.connector.headers should contain allElementsOf testHTTPHeaders
      controller.connector.path shouldBe "http://localhost:8236/oauth/token"
      jsonBodyOf(result) shouldBe Json.parse("""{"access_token":"495b5b1725d590eb87d0f6b7dcea32a9","refresh_token":"b75f2ed960898b4cd38f23934c6befb2","expires_in":14400}""")
    }

    "return 503 response when API Gateway returns a 5xx response " in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream5xxResponse("bad request", 500, 500))
      val result = await(controller.token()(jsonRequestWithAuthCode))

      status(result) shouldBe 503
      bodyOf(result) shouldBe ""
    }

    "return 401 response and no response body when API Gateway returns a BadRequest response" in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream4xxResponse("bad request", 400, 400))
      val result = await(controller.token()(jsonRequestWithAuthCode))

      status(result) shouldBe 401
      bodyOf(result) shouldBe ""
    }

    "return 401 and response body when API Gateway returns a BadRequest response " in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream4xxResponse(buildMessage("unauthorized", "some message"), 401, 401))
      val result = await(controller.token()(jsonRequestWithAuthCode))

      status(result) shouldBe 401
      bodyOf(result) shouldEqual buildMessage("unauthorized", "some message")
    }

    "return 401 and no response body when API Gateway returns an Unauthorized response " in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream4xxResponse("unauthorized", 401, 401))
      val result = await(controller.token()(jsonRequestWithAuthCode))

      status(result) shouldBe 401
      bodyOf(result) shouldBe ""
    }

    "return 401 and response body when API Gateway returns an Unauthorized response " in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream4xxResponse(buildMessage("unauthorized", "some message"), 401, 401))
      val result = await(controller.token()(jsonRequestWithAuthCode))

      status(result) shouldBe 401
      bodyOf(result) shouldBe buildMessage("unauthorized", "some message")
    }

    "return 401 and no response body when API Gateway returns an Unauthorized response with invalid Json body" in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream4xxResponse("{some invalid json", 401, 401))
      val result = await(controller.token()(jsonRequestWithAuthCode))

      status(result) shouldBe 401
      bodyOf(result) shouldBe ""
    }

    "return 503 response when API Gateway returns an incorrect response" in new BadResponseAPIGateway {
      val result = await(controller.token()(jsonRequestWithAuthCode))

      status(result) shouldBe 503
    }

  }

  "requesting a new access-token from a refresh-token" should {

    "successfully return access-token and refresh-token for a valid request + pass configured TxM HTTP headers to backend services " in new SuccessRefreshCode {
      val result = await(controller.token(Some("56789"))(addTestHeaders(jsonRequestRequestWithRefreshToken)))

      status(result) shouldBe 200
      controller.connector.headers should contain allElementsOf testHTTPHeaders
      controller.connector.path shouldBe "http://localhost:8236/oauth/token"
      jsonBodyOf(result) shouldBe Json.parse("""{"access_token":"495b5b1725d590eb87d0f6b7dcea32a9","refresh_token":"b75f2ed960898b4cd38f23934c6befb2","expires_in":14400}""")
    }

    "successfully return access-token and refresh-token for a valid request + pass lowercase TxM HTTP headers to backend services " in new SuccessRefreshCode {
      override val testHTTPHeaders = Seq(vendorHeader.toLowerCase() -> "header vendor", deviceIdHeader.toLowerCase() -> "header device Id")

      val result = await(controller.token(Some("56789"))(addTestHeaders(jsonRequestRequestWithRefreshToken)))

      status(result) shouldBe 200
      controller.connector.headers should contain allElementsOf testHTTPHeaders
      controller.connector.path shouldBe "http://localhost:8236/oauth/token"
      jsonBodyOf(result) shouldBe Json.parse("""{"access_token":"495b5b1725d590eb87d0f6b7dcea32a9","refresh_token":"b75f2ed960898b4cd38f23934c6befb2","expires_in":14400}""")
    }

    "successfully return access-token and refresh-token for a valid request + no additional HTTP headers supplied to backend services" in new SuccessRefreshCode  {
      val result = await(controller.token(Some("56789"))(jsonRequestRequestWithRefreshToken))

      status(result) shouldBe 200
      controller.connector.headers.size shouldBe 1
      controller.connector.headers.exists(item => item._1 == "X-Request-Chain") shouldBe true
      controller.connector.path shouldBe "http://localhost:8236/oauth/token"
      jsonBodyOf(result) shouldBe Json.parse("""{"access_token":"495b5b1725d590eb87d0f6b7dcea32a9","refresh_token":"b75f2ed960898b4cd38f23934c6befb2","expires_in":14400}""")
    }

    "successfully return access-token and refresh-token for a valid request with expires_in value decremented by config amount of 400" in new SuccessExpiryDecrement(400L) {
      val result = await(controller.token(Some("56789"))(jsonRequestRequestWithRefreshToken))

      status(result) shouldBe 200
      controller.connector.headers.size shouldBe 1
      controller.connector.headers.exists(item => item._1 == "X-Request-Chain") shouldBe true
      controller.connector.path shouldBe "http://localhost:8236/oauth/token"
      jsonBodyOf(result) shouldBe Json.parse("""{"access_token":"495b5b1725d590eb87d0f6b7dcea32a9","refresh_token":"b75f2ed960898b4cd38f23934c6befb2","expires_in":14000}""")
    }

    "successfully return access-token and refresh-token for a valid request with expires_in value not decremented by config amount if config amount > expires_in value" in new SuccessExpiryDecrement(14401L) {
      val result = await(controller.token(Some("56789"))(jsonRequestRequestWithRefreshToken))

      status(result) shouldBe 200
      controller.connector.headers.size shouldBe 1
      controller.connector.headers.exists(item => item._1 == "X-Request-Chain") shouldBe true
      controller.connector.path shouldBe "http://localhost:8236/oauth/token"
      jsonBodyOf(result) shouldBe Json.parse("""{"access_token":"495b5b1725d590eb87d0f6b7dcea32a9","refresh_token":"b75f2ed960898b4cd38f23934c6befb2","expires_in":14400}""")
    }

    "return 503 and no response body when API Gateway returns a 500 response " in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream5xxResponse("Server error", 500, 500))
      val result = await(controller.token()(jsonRequestWithRefreshCode))

      status(result) shouldBe 503
    }

    "return 503 and response body when API Gateway returns a 500 response " in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream5xxResponse(buildMessage("unauthorized", "some message"), 500, 500))
      val result = await(controller.token()(jsonRequestWithRefreshCode))

      status(result) shouldBe 503
      bodyOf(result) shouldBe buildMessage("unauthorized", "some message")
    }

    "return 401 and no response body when API Gateway returns a 400 response " in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream4xxResponse("bad request", 400, 400))
      val result = await(controller.token()(jsonRequestWithRefreshCode))

      status(result) shouldBe 401
      bodyOf(result) shouldBe ""
    }

    "return 401 and response body when API Gateway returns a 400 response " in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream4xxResponse(buildMessage("unauthorized", "some message"), 400, 400))
      val result = await(controller.token()(jsonRequestWithRefreshCode))

      status(result) shouldBe 401
      bodyOf(result) shouldBe buildMessage("unauthorized", "some message")
    }

    "return 401 and no response body when API Gateway returns a 401 response " in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream4xxResponse("unauthorized", 401, 401))
      val result = await(controller.token()(jsonRequestWithRefreshCode))

      status(result) shouldBe 401
      bodyOf(result) shouldBe ""
    }

    "return 401 and response body when API Gateway returns a 401 response " in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream4xxResponse(buildMessage("unauthorized", "some message"), 401, 401))
      val result = await(controller.token()(jsonRequestWithRefreshCode))

      status(result) shouldBe 401
      bodyOf(result) shouldBe buildMessage("unauthorized", "some message")
    }

    "return 401 and no response body when API Gateway returns a 401 response with invalid response body" in new FailToReturnApiToken {
      override lazy val ex = Some(Upstream4xxResponse("""{this is incorrect json""", 401, 401))
      val result = await(controller.token()(jsonRequestWithRefreshCode))

      status(result) shouldBe 401
      bodyOf(result) shouldBe ""
    }

    "return 503 and no response body when response from API Gateway returns an incorrect response" in new BadResponseAPIGateway {
      val result = await(controller.token()(jsonRequestWithRefreshCode))

      status(result) shouldBe 503
      bodyOf(result) shouldBe ""
    }

  }

  "requesting the authorize service" should {

    "return a 303 redirect with the URL to the API Gateway authorize service" in new SuccessRefreshCode {
      val result = await(controller.authorize()(addTestHeaders(emptyRequest)))

      status(result) shouldBe 303
      header("Location", result).get shouldBe "http://localhost:8236/oauth/authorize?client_id=client_id&redirect_uri=redirect_uri&scope=some-scopes&response_type=code"
      header(vendorHeader, result).get shouldBe "header vendor"
      header(deviceIdHeader, result).get shouldBe "header device Id"
    }
  }

  "requesting the tax-calc service" should {

    "return a JSON response which contains the AES encrypted token" in new SuccessRefreshCode {
      val result = await(controller.taxcalctoken()(emptyRequest))

      status(result) shouldBe 200

      val response = jsonBodyOf(result).as[TokenResponse]
      val aes = CryptoWithKeysFromConfig("aes")
      aes.decrypt(Crypted(response.token)).value shouldBe controller.appConfig.tax_calc_token
    }

  }

}
