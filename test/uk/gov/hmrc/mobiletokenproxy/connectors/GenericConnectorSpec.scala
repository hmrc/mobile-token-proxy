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

import izumi.reflect.Tag
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json.parse
import play.api.libs.json.JsValue
import play.api.libs.ws.BodyWritable
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.mobiletokenproxy.controllers.StubApplicationConfiguration

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class GenericConnectorSpec
    extends AnyWordSpecLike
    with ScalaFutures
    with StubApplicationConfiguration
    with MockFactory
    with Matchers {

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  implicit val mockHttpClient:     HttpClientV2     = mock[HttpClientV2]
  implicit val mockRequestBuilder: RequestBuilder   = mock[RequestBuilder]
  val connector:                   GenericConnector = new GenericConnector(mockHttpClient)

  val url = "https://somepath"
  val someJson: JsValue                  = parse("""{"test":1234}""")
  val somePost: Map[String, Seq[String]] = Map("test" -> Seq("1234"))

  val http500Response: Future[HttpResponse] = Future.failed(UpstreamErrorResponse("Error", 500, 500))
  val http400Response: Future[HttpResponse] = Future.failed(new BadRequestException("bad request"))
  val http200Response: Future[HttpResponse] = Future.successful(HttpResponse(200, someJson.toString()))

  "genericConnector get" should {
    def getReturning[T](response: Future[T]) = {

      (mockHttpClient
        .get(_: URL)(_: HeaderCarrier))
        .expects(*, *)
        .returns(mockRequestBuilder)

      (mockRequestBuilder
        .execute[T](_: HttpReads[T], _: ExecutionContext))
        .expects(*, *)
        .returns(response)
    }

    "throw BadRequestException on 400 response" in {
      getReturning(http400Response)

      connector.doGet(url) onComplete {
        case Success(_) => fail()
        case Failure(_) =>
      }

    }

    "throw Upstream5xxResponse on 500 response" in {
      getReturning(http500Response)

      connector.doGet(url) onComplete {
        case Success(_) => fail()
        case Failure(_) =>
      }

    }

    "return JSON response on 200 success" in {
      getReturning(http200Response)
      connector.doGet(url).futureValue.json shouldBe someJson
    }
  }

  "genericConnector post" should {
    def postReturning[T](response: Future[T]) = {

      (mockHttpClient
        .post(_: URL)(_: HeaderCarrier))
        .expects(*, *)
        .returns(mockRequestBuilder)

      (mockRequestBuilder
        .withBody(_: T)(_: BodyWritable[T], _: Tag[T], _: ExecutionContext))
        .expects(*, *, *, *)
        .returns(mockRequestBuilder)

      (mockRequestBuilder
        .execute[T](_: HttpReads[T], _: ExecutionContext))
        .expects(*, *)
        .returns(response)

    }

    "throw BadRequestException on 400 response" in {
      postReturning(http400Response)
      connector.doPost(url, someJson) onComplete {
        case Success(_) => fail()
        case Failure(_) =>
      }

    }

    "throw Upstream5xxResponse on 500 response" in {
      postReturning(http500Response)

      connector.doPost(url, someJson) onComplete {
        case Success(_) => fail()
        case Failure(_) =>
      }

    }

    "return JSON response on 200 success" in {
      postReturning(http200Response)
      connector.doPost(url, someJson).futureValue.json shouldBe someJson
    }
  }

  "genericConnector post Form" should {

    def postFormReturning[T](response: Future[T]) = {

      (mockHttpClient
        .post(_: URL)(_: HeaderCarrier))
        .expects(*, *)
        .returns(mockRequestBuilder)

      (mockRequestBuilder
        .withBody(_: Map[String, Seq[String]])(_: BodyWritable[Map[String, Seq[String]]],
                                               _: Tag[Map[String, Seq[String]]],
                                               _: ExecutionContext))
        .expects(*, *, *, *)
        .returns(mockRequestBuilder)

      (mockRequestBuilder
        .execute[T](_: HttpReads[T], _: ExecutionContext))
        .expects(*, *)
        .returns(response)
    }

    "return JSON response on 200 success" in {
      postFormReturning(http200Response)
      connector.doPostForm(url, somePost).futureValue.json shouldBe someJson
    }

    "throw BadRequestException on 400 response" in {
      postFormReturning(http400Response)
      connector.doPostForm(url, somePost) onComplete {
        case Success(_) => fail()
        case Failure(_) =>
      }

    }

    "throw Upstream5xxResponse on 500 response" in {
      postFormReturning(http500Response)

      connector.doPostForm(url, somePost) onComplete {
        case Success(_) => fail()
        case Failure(_) =>
      }

    }

  }
}
