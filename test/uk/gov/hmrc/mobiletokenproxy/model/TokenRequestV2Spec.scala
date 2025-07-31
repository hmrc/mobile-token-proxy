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

class TokenRequestV2Spec extends AnyWordSpec with Matchers {

  "TokenRequestV2 JSON serialization" should {

    "serialize to JSON correctly" in {
      val request = TokenRequestV2(
        authorizationCode = Some(Seq("authCode1", "authCode2")),
        refreshToken = Some(Seq("refresh1", "refresh2"))
      )

      val json = Json.toJson(request)
      val expectedJson = Json.parse(
        """
          |{
          |  "authorizationCode": ["authCode1", "authCode2"],
          |  "refreshToken": ["refresh1", "refresh2"]
          |}
          |""".stripMargin)

      json shouldBe expectedJson
    }

    "deserialize from JSON correctly" in {
      val json = Json.parse(
        """
          |{
          |  "authorizationCode": ["code1"],
          |  "refreshToken": ["refreshToken1"]
          |}
          |""".stripMargin)

      val expected = TokenRequestV2(
        authorizationCode = Some(Seq("code1")),
        refreshToken = Some(Seq("refreshToken1"))
      )

      json.as[TokenRequestV2] shouldBe expected
    }

    "handle empty JSON as TokenRequestV2 with None values" in {
      val json = Json.parse("{}")
      val expected = TokenRequestV2(None, None)

      json.as[TokenRequestV2] shouldBe expected
    }

    "serialize and deserialize an empty TokenRequestV2 correctly" in {
      val original = TokenRequestV2(None, None)
      val json = Json.toJson(original)
      val parsed = json.as[TokenRequestV2]

      parsed shouldBe original
    }
  }
}
