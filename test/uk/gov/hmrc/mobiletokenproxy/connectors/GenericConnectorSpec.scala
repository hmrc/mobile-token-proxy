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

package uk.gov.hmrc.mobiletokenproxy.connectors


import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json.parse
import play.api.libs.json.{JsValue, Writes}
import play.api.test.FakeApplication
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse, Upstream5xxResponse}
import uk.gov.hmrc.mobiletokenproxy.config.HttpVerbs
import uk.gov.hmrc.mobiletokenproxy.controllers.StubApplicationConfiguration
import uk.gov.hmrc.play.test._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// NGC-3236 review
class GenericConnectorSpec
  extends UnitSpec with WithFakeApplication with ScalaFutures with StubApplicationConfiguration {

  override lazy val fakeApplication = FakeApplication(additionalConfiguration = config)

  private trait Setup {

    implicit lazy val hc: HeaderCarrier = HeaderCarrier()

    val someJson: JsValue = parse("""{"test":1234}""")
    lazy val http500Response: Future[Nothing] = Future.failed(Upstream5xxResponse("Error", 500, 500))
    lazy val http400Response: Future[Nothing] = Future.failed(new BadRequestException("bad request"))
    lazy val http200Response: Future[AnyRef with HttpResponse] = Future.successful(HttpResponse(200, Some(someJson)))
    lazy val response: Future[HttpResponse] = http400Response

    val http: HttpVerbs = new HttpVerbs {
      override val hooks: Seq[HttpHook] = NoneRequired
      override def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = response

      override def doPost[A](url: String, body: A, headers: Seq[(String, String)])(implicit wts: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = response

      override def doPostString(url: String, body: String, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[HttpResponse] = response

      override def doFormPost(url: String, body: Map[String, Seq[String]])(implicit hc: HeaderCarrier): Future[HttpResponse] = response

      override def doEmptyPost[A](url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = response
    }

    val connector = new GenericConnector(http)
  }

  "genericConnector get" should {
    "throw BadRequestException on 400 response" in new Setup {

      intercept[BadRequestException] {
        await(connector.doGet("somepath"))
      }
    }

    "throw Upstream5xxResponse on 500 response" in new Setup {
      override lazy val response: Future[HttpResponse] = http500Response

      intercept[Upstream5xxResponse] {
        await(connector.doGet("somepath"))
      }
    }

    "return JSON response on 200 success" in new Setup {
      override lazy val response: Future[HttpResponse] = http200Response

      await(connector.doGet("somepath")).json shouldBe someJson
    }
  }

  "genericConnector post" should {
    "throw BadRequestException on 400 response" in new Setup {

      intercept[BadRequestException] {
        await(connector.doPost("somepath", someJson))
      }
    }

    "throw Upstream5xxResponse on 500 response" in new Setup {
      override lazy val response: Future[HttpResponse] = http500Response

      intercept[Upstream5xxResponse] {
        await(connector.doPost("somepath", someJson))
      }
    }

    "return JSON response on 200 success" in new Setup {
      override lazy val response: Future[HttpResponse] = http200Response

      await(connector.doPost("somepath", someJson)).json shouldBe someJson
    }
  }
}
