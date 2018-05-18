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

import com.google.inject.{Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.mobiletokenproxy.config.HttpVerbs

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class  GenericConnector @Inject()(val http: HttpVerbs) {
  def doGet(path:String)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
    http.GET(path)
  }

  def doPost(path:String, json:JsValue)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
    http.POST(path, json)
  }

  def doPostForm(path:String, form:Map[String,Seq[String]])(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
    http.POSTForm(path, form)
  }
}
