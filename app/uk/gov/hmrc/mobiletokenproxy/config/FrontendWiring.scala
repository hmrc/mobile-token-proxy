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

package uk.gov.hmrc.mobiletokenproxy.config

import com.google.inject.Singleton
import javax.inject.Inject
import play.api.Configuration
import play.api.i18n.MessagesApi
import play.api.mvc.Request
import play.twirl.api.Html
import uk.gov.hmrc.http.hooks.{HttpHook, HttpHooks}
import uk.gov.hmrc.http.{HttpGet, HttpPost}
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import uk.gov.hmrc.play.http.ws._


class ErrorHandler @Inject()(val messagesApi: MessagesApi, val configuration: Configuration) extends FrontendErrorHandler {
  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit request: Request[_]): Html =
     views.html.global_error(pageTitle, heading, message)
}

trait HttpVerbs extends HttpGet with WSGet with HttpPost with WSPost with HttpHooks {
  override val hooks: Seq[HttpHook] = NoneRequired
}

@Singleton
class WSHttp extends HttpVerbs
