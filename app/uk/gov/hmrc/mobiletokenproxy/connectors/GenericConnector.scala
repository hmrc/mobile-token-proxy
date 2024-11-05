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

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GenericConnector @Inject() (val http: HttpClientV2) {

  def doGet(
    path:        String
  )(implicit ec: ExecutionContext,
    hc:          HeaderCarrier
  ): Future[HttpResponse] =
    http
      .get(url"$path")
      .execute[HttpResponse]

  def doPost(
    path:        String,
    json:        JsValue
  )(implicit ec: ExecutionContext,
    hc:          HeaderCarrier
  ): Future[HttpResponse] =
    http
      .post(url"$path")
      .withBody(json)
      .execute[HttpResponse]

  def doPostForm(
    path:        String,
    form:        Map[String, Seq[String]]
  )(implicit ec: ExecutionContext,
    hc:          HeaderCarrier
  ): Future[HttpResponse] =
    http
      .post(url"$path")
      .withBody(Json.toJson(form))
      .execute[HttpResponse]
}
