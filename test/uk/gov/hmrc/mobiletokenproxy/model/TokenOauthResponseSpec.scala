/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.mobiletokenproxy.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*

class TokenOauthResponseSpec extends AnyWordSpec with Matchers {

  "TokenOauthResponse JSON serialization" should {

    "serialize to JSON correctly" in {
      val response = TokenOauthResponse(
        access_token  = "access123",
        refresh_token = "refresh123",
        expires_in    = 3600L
      )

      val json = Json.toJson(response)
      val expectedJson = Json.parse("""
          |{
          |  "access_token": "access123",
          |  "refresh_token": "refresh123",
          |  "expires_in": 3600
          |}
          |""".stripMargin)

      json shouldBe expectedJson
    }

    "deserialize from JSON correctly" in {
      val json = Json.parse("""
          |{
          |  "access_token": "tokenABC",
          |  "refresh_token": "tokenXYZ",
          |  "expires_in": 7200
          |}
          |""".stripMargin)

      val expected = TokenOauthResponse(
        access_token  = "tokenABC",
        refresh_token = "tokenXYZ",
        expires_in    = 7200L
      )

      json.as[TokenOauthResponse] shouldBe expected
    }

    "fail deserialization if a required field is missing" in {
      val invalidJson = Json.parse("""
          |{
          |  "access_token": "onlyAccess"
          |}
          |""".stripMargin)

      assertThrows[JsResultException] {
        invalidJson.as[TokenOauthResponse]
      }
    }
  }
}
