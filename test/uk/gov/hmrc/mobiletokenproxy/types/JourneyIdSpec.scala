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

package uk.gov.hmrc.mobiletokenproxy.types

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.{PathBindable, QueryStringBindable}

class JourneyIdSpec extends AnyFlatSpec with Matchers {

  "JourneyId.from" should "succeed for valid UUID string" in {
    val validId = "123e4567-e89b-12d3-a456-426614174000"
    val result = JourneyId.from(validId)

    result.isRight shouldBe true
    result.foreach(_.value.value shouldBe validId)
  }

  it should "fail for invalid UUID strings" in {
    val invalidId = "invalid-uuid"
    val result = JourneyId.from(invalidId)

    result.isLeft shouldBe true
  }

  "QueryStringBindable[JourneyId]" should "bind and unbind correctly" in {
    val validId = "123e4567-e89b-12d3-a456-426614174000"
    val bindable = implicitly[QueryStringBindable[JourneyId]]

    val bound = bindable.bind("id", Map("id" -> Seq(validId)))
    bound     shouldBe defined
    bound.get shouldBe JourneyId.from(validId)
  }

  it should "fail to bind from an invalid query string" in {
    val invalidId = "not-a-valid-id"
    val bindable = implicitly[QueryStringBindable[JourneyId]]

    val bound = bindable.bind("id", Map("id" -> Seq(invalidId)))

    bound            shouldBe defined
    bound.get.isLeft shouldBe true
  }

  it should "unbind to a query string value correctly" in {
    val validId = "123e4567-e89b-12d3-a456-426614174000"
    val bindable = implicitly[QueryStringBindable[JourneyId]]

    val journeyId = JourneyId.from(validId).toOption.get
    val unbound = bindable.unbind("id", journeyId)

    unbound shouldBe validId
  }

  "PathBindable[JourneyId]" should "bind correctly from a valid path segment" in {
    val validId = "123e4567-e89b-12d3-a456-426614174000"
    val bindable = implicitly[PathBindable[JourneyId]]

    val bound = bindable.bind("id", validId)

    bound shouldBe JourneyId.from(validId)
  }

  it should "fail to bind from an invalid path segment" in {
    val invalidId = "bad"
    val bindable = implicitly[PathBindable[JourneyId]]

    val bound = bindable.bind("id", invalidId)

    bound.isLeft shouldBe true
  }

}
