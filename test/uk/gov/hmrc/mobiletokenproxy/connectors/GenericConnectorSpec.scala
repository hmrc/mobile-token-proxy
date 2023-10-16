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

package uk.gov.hmrc.mobiletokenproxy.connectors

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json.parse
import play.api.libs.json.{JsValue, Writes}
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobiletokenproxy.config.HttpVerbs
import uk.gov.hmrc.mobiletokenproxy.controllers.StubApplicationConfiguration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

class GenericConnectorSpec
    extends AnyWordSpecLike
    with ScalaFutures
    with StubApplicationConfiguration
    with MockFactory
    with Matchers {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  val mockHttp:  HttpVerbs        = mock[HttpVerbs]
  val connector: GenericConnector = new GenericConnector(mockHttp)

  val url = "somepath"
  val someJson: JsValue = parse("""{"test":1234}""")

  val http500Response: Future[HttpResponse] = Future.failed(UpstreamErrorResponse("Error", 500, 500))
  val http400Response: Future[HttpResponse] = Future.failed(new BadRequestException("bad request"))
  val http200Response: Future[HttpResponse] = Future.successful(HttpResponse(200, someJson.toString()))

  "genericConnector get" should {
    def getReturning(response: Future[HttpResponse]) =
      (mockHttp
        .GET(_: String, _: Seq[(String, String)], _: Seq[(String, String)])(_: HttpReads[HttpResponse],
                                                                            _: HeaderCarrier,
                                                                            _: ExecutionContext))
        .expects(url, *, *, *, *, *)
        .returning(response)

    "throw BadRequestException on 400 response" in {
      getReturning(http400Response)

      intercept[BadRequestException] {
        Await.result(connector.doGet(url), 10 seconds)
      }
    }

    "throw Upstream5xxResponse on 500 response" in {
      getReturning(http500Response)

      intercept[UpstreamErrorResponse] {
        Await.result(connector.doGet(url), 10 seconds)
      }
    }

    "return JSON response on 200 success" in {
      getReturning(http200Response)

      connector.doGet(url).futureValue.json shouldBe someJson
    }
  }

  "genericConnector post" should {
    def postReturning(response: Future[HttpResponse]) =
      (mockHttp
        .POST(_: String, _: JsValue, _: Seq[(String, String)])(_: Writes[JsValue],
                                                               _: HttpReads[HttpResponse],
                                                               _: HeaderCarrier,
                                                               _: ExecutionContext))
        .expects(url, someJson, *, *, *, *, *)
        .returning(response)

    "throw BadRequestException on 400 response" in {
      postReturning(http400Response)

      intercept[BadRequestException] {
        Await.result(connector.doPost(url, someJson), 10 seconds)
      }
    }

    "throw Upstream5xxResponse on 500 response" in {
      postReturning(http500Response)

      intercept[UpstreamErrorResponse] {
        Await.result(connector.doPost(url, someJson), 10 seconds)
      }
    }

    "return JSON response on 200 success" in {
      postReturning(http200Response)

      connector.doPost(url, someJson).futureValue.json shouldBe someJson
    }
  }
}
