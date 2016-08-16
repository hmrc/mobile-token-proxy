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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}

class TestSpec extends UnitSpec with WithFakeApplication with ScalaFutures with BeforeAndAfterEach {

  "token request payloads" should {

    "return BadRequest if the authorization code or ueid is missing" in new SuccessAccessCode {
      val result = await(controller.token()(jsonRequestWithDeviceId))

      status(result) shouldBe 400
    }

    "return BadRequest if the request is empty" in new SuccessAccessCode {
      val result = await(controller.token()(jsonRequestEmpty))

      status(result) shouldBe 400
    }

    "return BadRequest if both authorization code and ueid is supplied" in new SuccessAccessCode {
      val result = await(controller.token()(jsonRequestRequestWithAuthCodeAndUeid))

      status(result) shouldBe 400
    }
  }

  "requesting a new access-token from an access-code" should {

    "successfully return access-token and UEID for a valid request " in new SuccessAccessCode {
      val result = await(controller.token()(jsonRequestRequestWithAuth))

      val ueid = buildUEID(deviceId, recordId)

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse( s"""{"accessToken":"495b5b1725d590eb87d0f6b7dcea32a9","ueid":"$ueid","expireTime":14400}""")
    }

    "fail with ServiceUnavailable response when API Gateway returns a non 200 response " in new FailToReturnApiToken {
      val result = await(controller.token()(jsonRequestRequestWithAuth))

      status(result) shouldBe 503
    }

    "fail with Unauthorized response when API Gateway returns a BadRequest response " in new BadRequestExceptionReturnApiToken {
      val result = await(controller.token()(jsonRequestRequestWithAuth))

      status(result) shouldBe 401
    }

    "return 500 response when response from API Gateway returns an incorrect response" in new BadResponseAPIGateway {
      val result = await(controller.token()(jsonRequestRequestWithAuth))

      status(result) shouldBe 500
    }

    "return the access-token without a UEID when the saving of the token fails" in new FailToSaveToken {
      val result = await(controller.token()(jsonRequestRequestWithAuth))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse( s"""{"accessToken":"495b5b1725d590eb87d0f6b7dcea32a9","expireTime":14400}""")
    }
  }

// TODO...ADD TEST FOR WHEN THE UEID DOES NOT MATCH!!!
// TODO...THE REFRESH TOKEN IS UPDATED!!!

  "requesting a new access-token from a UEID token" should {

    "successfully return access-token and UEID for a valid request " in new SuccessRefreshCode {
      val result = await(controller.token()(jsonRequestRequestWithUeid))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse(s"""{"accessToken":"495b5b1725d590eb87d0f6b7dcea32a9","ueid":"$ueid","expireTime":14400}""")
    }

    "return NotFound when the deviceId cannot be resolved" in new NotFoundDeviceIdRefresh {
      val result = await(controller.token()(jsonRequestRequestWithUeid))

      status(result) shouldBe 404
    }

    "return 500 when finding the deviceId fails" in new FailToFindTokenForRefresh {
      val result = await(controller.token()(jsonRequestRequestWithUeid))

      status(result) shouldBe 500
    }

    "return access-token and no UEID when fail to save the updated refresh token" in new FailToSaveTokenForRefresh {
      val result = await(controller.token()(jsonRequestRequestWithUeid))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.parse(s"""{"accessToken":"495b5b1725d590eb87d0f6b7dcea32a9","expireTime":14400}""")
    }

  }

}
