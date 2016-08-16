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

package uk.gov.hmrc.mobiletokenproxy.connectors


import play.api.test.FakeApplication
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.test._
import uk.gov.hmrc.mobiletokenproxy.controllers.StubApplicationConfiguration
import scala.concurrent.Future
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.play.http._
import scala.concurrent.ExecutionContext.Implicits.global


class GenericTestConnector extends GenericConnector with ServicesConfig {
  override def http: HttpGet with HttpPost = ???
}

class GenericConnectorSpec
  extends UnitSpec with WithFakeApplication with ScalaFutures with StubApplicationConfiguration {

  override lazy val fakeApplication = FakeApplication(additionalConfiguration = config)

  private trait Setup {

    implicit lazy val hc = HeaderCarrier()

    val someJson = Json.parse("""{"test":1234}""")
    lazy val http500Response = Future.failed(new Upstream5xxResponse("Error", 500, 500))
    lazy val http400Response = Future.failed(new BadRequestException("bad request"))
    lazy val http200Response = Future.successful(HttpResponse(200, Some(someJson)))
    lazy val response: Future[HttpResponse] = http400Response

    val connector = new GenericTestConnector {

      override lazy val http: HttpPost with HttpGet = new HttpGet with HttpPost {

        override val hooks: Seq[HttpHook] = NoneRequired
        override protected def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = response

        override protected def doPost[A](url: String, body: A, headers: Seq[(String, String)])(implicit wts: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = response

        override protected def doPostString(url: String, body: String, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[HttpResponse] = response

        override protected def doFormPost(url: String, body: Map[String, Seq[String]])(implicit hc: HeaderCarrier): Future[HttpResponse] = response

        override protected def doEmptyPost[A](url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = response
      }
    }
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
