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

import com.google.inject.AbstractModule
import com.google.inject.name.Names.named
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.mobiletokenproxy.services.{LiveTokenServiceImpl, TokenService}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.util.Try

class GuiceModule (environment: Environment, configuration: Configuration) extends AbstractModule with ServicesConfig{
  override protected lazy val mode: Mode = environment.mode
  override protected lazy val runModeConfiguration: Configuration = configuration

  override def configure(): Unit = {
    bindConfigString("google-analytics.token")
    bindConfigString("google-analytics.host")
    bindConfigString("api-gateway.pathToAPIGatewayTokenService")
    bindConfigString("api-gateway.pathToAPIGatewayAuthService")
    bindConfigString("api-gateway.scope")
    bindConfigString("api-gateway.response_type")
    bindConfigString("api-gateway.client_id")
    bindConfigString("api-gateway.redirect_uri")
    bindConfigString("api-gateway.client_secret")
    bindConfigString("api-gateway.tax_calc_server_token")
    bindConfigLongDefaultToZero("api-gateway.expiry_decrement")
    bind(classOf[HttpVerbs]).to(classOf[WSHttp])
    bind(classOf[TokenService]).to(classOf[LiveTokenServiceImpl])

    bind(classOf[ProxyPassthroughHttpHeaders]).toInstance(
      new ProxyPassthroughHttpHeaders(configuration.getStringSeq(
        "api-gateway.proxyPassthroughHttpHeaders").getOrElse(Seq.empty)))
  }

  private def bindConfigLongDefaultToZero(path: String): Unit = {
    val l: Long = Try(configuration.underlying.getLong(path)).getOrElse(0)
    bindConstant().annotatedWith(named(path)).to(l)
  }

  private def bindConfigString(path: String): Unit = {
    bindConstant().annotatedWith(named(path)).to(configuration.underlying.getString(path))
  }
}