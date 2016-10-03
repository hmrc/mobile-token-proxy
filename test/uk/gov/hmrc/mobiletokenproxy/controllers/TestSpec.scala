/*
 * Copyright 2016 HM Revenue & Customs
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

import org.apache.commons.codec.binary.Base64
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.crypto.{PlainText, Crypted, CryptoWithKeysFromConfig, RSAEncryptDecrypt}
import uk.gov.hmrc.mobiletokenproxy.model.TokenResponse
import uk.gov.hmrc.play.http.{UnauthorizedException, BadRequestException, InternalServerException}
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}

class TestSpec extends UnitSpec with WithFakeApplication with ScalaFutures with BeforeAndAfterEach with StubApplicationConfiguration {

  val encryptionKey = Base64.encodeBase64String(Array[Byte](0, 1, 2, 3, 4, 5 ,6 ,7, 8 ,9, 10, 11, 12, 13, 14, 15))
  val fakeApplicationWithCurrentKeyOnly = FakeApplication(additionalConfiguration = Map(
    "aes.key" -> encryptionKey
  ))

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
      val result = await(controller.token()(jsonRequestRequestWithAuthCode))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse("""{"access_token":"495b5b1725d590eb87d0f6b7dcea32a9","refresh_token":"b75f2ed960898b4cd38f23934c6befb2","expires_in":14400}""")
    }

    "return 503 response when API Gateway returns a non 200 response " in new FailToReturnApiToken {
      override lazy val ex = Some(new InternalServerException("Not Found"))
      val result = await(controller.token()(jsonRequestRequestWithAuthCode))

      status(result) shouldBe 503
    }

    "return 401 response when API Gateway returns a BadRequest response " in new FailToReturnApiToken {
      override lazy val ex = Some(new BadRequestException("bad request"))
      val result = await(controller.token()(jsonRequestRequestWithAuthCode))

      status(result) shouldBe 401
    }

    "return 401 response when API Gateway returns an Unauthorized response " in new FailToReturnApiToken {
      override lazy val ex = Some(new UnauthorizedException("unauthoried"))
      val result = await(controller.token()(jsonRequestRequestWithAuthCode))

      status(result) shouldBe 401
    }

    "return 503 response when API Gateway returns an incorrect response" in new BadResponseAPIGateway {
      val result = await(controller.token()(jsonRequestRequestWithAuthCode))

      status(result) shouldBe 503
    }

  }

  "requesting a new access-token from a refresh-token" should {

    "successfully return access-token and refresh-token for a valid request " in new SuccessRefreshCode {
      val result = await(controller.token()(jsonRequestRequestWithRefreshToken))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse("""{"access_token":"495b5b1725d590eb87d0f6b7dcea32a9","refresh_token":"b75f2ed960898b4cd38f23934c6befb2","expires_in":14400}""")
    }

    "return 503 response when API Gateway returns a 500 response " in new FailToReturnApiToken {
      override lazy val ex = Some(new InternalServerException("Not Found"))
      val result = await(controller.token()(jsonRequestRequestWithRefreshCode))

      status(result) shouldBe 503
    }

    "return 401 response when API Gateway returns a 400 response " in new FailToReturnApiToken {
      override lazy val ex = Some(new BadRequestException("bad request"))
      val result = await(controller.token()(jsonRequestRequestWithRefreshCode))

      status(result) shouldBe 401
    }

    "return 401 response when API Gateway returns a 401 response " in new FailToReturnApiToken {
      override lazy val ex = Some(new UnauthorizedException("unauthoried"))
      val result = await(controller.token()(jsonRequestRequestWithRefreshCode))

      status(result) shouldBe 401
    }

    "return 503 response when response from API Gateway returns an incorrect response" in new BadResponseAPIGateway {
      val result = await(controller.token()(jsonRequestRequestWithRefreshCode))

      status(result) shouldBe 503
    }

  }

  "requesting the authorize service" should {

    "return a 303 redirect with the URL to the API Gateway authorize service" in new SuccessRefreshCode {
      val result = await(controller.authorize()(emptyRequest))

      status(result) shouldBe 303
      header("Location", result).get shouldBe "http://localhost:8236/oauth/authorize?client_id=client_id&redirect_uri=redirect_uri&scope=some-scopes&response_type=code"
    }
  }

  "requesting the tax-calc service" should {

    "return a JSON response which contains the RSA encrypted token" in new SuccessRefreshCode {
      val result = await(controller.taxcalctoken()(emptyRequest))

      status(result) shouldBe 200

      val response = jsonBodyOf(result).as[TokenResponse]
      val aes = CryptoWithKeysFromConfig("aes")
      aes.decrypt(Crypted(response.token)).value shouldBe controller.appConfig.tax_calc_token
    }

  }

}
