/*
 * Copyright 2017 HM Revenue & Customs
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

import akka.stream.Materializer
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.Play.current
import play.api.mvc.{EssentialFilter, Request}
import play.api.{Application, Configuration, Play}
import play.filters.csrf.CSRFFilter
import play.twirl.api.Html
import uk.gov.hmrc.http.hooks.{HttpHook, HttpHooks}
import uk.gov.hmrc.http.{HttpDelete, HttpGet, HttpPost, HttpPut}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector => Auditing}
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal
import uk.gov.hmrc.play.frontend.config.LoadAuditingConfig
import uk.gov.hmrc.play.frontend.filters.{CSRFExceptionsFilter, FrontendAuditFilter, FrontendLoggingFilter, MicroserviceFilterSupport}
import uk.gov.hmrc.play.http.ws._


object ApplicationGlobal
  extends DefaultFrontendGlobal {

  override def frontendFilters: Seq[EssentialFilter] = {
    defaultFrontendFilters.filterNot(
      e => e.isInstanceOf[CSRFFilter] || e.isInstanceOf[CSRFExceptionsFilter.type]
    )
  }

  override lazy val auditConnector = AuditConnector
  override lazy val loggingFilter = LoggingFilter
  override lazy val frontendAuditFilter = AuditFilter

  override def onStart(app: Application) {
    super.onStart(app)
  }

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit request: Request[_]): Html =
    views.html.global_error(pageTitle, heading, message)

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")
}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}


object LoggingFilter extends FrontendLoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}


object AuditFilter extends FrontendAuditFilter with RunMode with AppName {

  override lazy val maskedFormFields = Seq.empty

  override lazy val applicationPort = None

  override lazy val auditConnector = AuditConnector

  override def controllerNeedsAuditing(controllerName: String): Boolean= ControllerConfiguration.paramsForController(controllerName).needsAuditing

  override implicit def mat: Materializer = Play.materializer
}


object AuditConnector extends Auditing with AppName with RunMode {
  override lazy val auditingConfig = {
    LoadAuditingConfig(s"auditing")
  }
}

trait Hooks extends HttpHooks {
  override val hooks: Seq[HttpHook] = NoneRequired
}

trait WSHttp extends HttpGet with WSGet with HttpPut with WSPut with HttpPost with WSPost with HttpDelete with WSDelete with Hooks with AppName
object WSHttp extends WSHttp
