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

import javax.inject.Singleton
import play.api.Configuration
import play.api.Mode.Mode
import play.api.Play._
import uk.gov.hmrc.play.config.ServicesConfig

@Singleton
class ApplicationConfig extends ServicesConfig {
  // NGC-3236 review
  private def loadConfig(key: String): String = configuration.getString(key).getOrElse(throw new RuntimeException(s"Missing key: $key"))
  private def loadConfigLong(key: String): Option[Long] = configuration.getLong(key)

  val passthroughHttpHeaderKey = "api-gateway.proxyPassthroughHttpHeaders"

   lazy val analyticsToken: Option[String] =  Some(loadConfig("google-analytics.token"))
   lazy val analyticsHost: String = loadConfig("google-analytics.host")

   lazy val pathToAPIGatewayTokenService: String = loadConfig("api-gateway.pathToAPIGatewayTokenService")
   lazy val pathToAPIGatewayAuthService: String = loadConfig("api-gateway.pathToAPIGatewayAuthService")
   lazy val scope: String = loadConfig("api-gateway.scope")
   lazy val response_type: String = loadConfig("api-gateway.response_type")
   lazy val client_id: String = loadConfig("api-gateway.client_id")
   lazy val redirect_uri: String = loadConfig("api-gateway.redirect_uri")
   lazy val client_secret: String = loadConfig("api-gateway.client_secret")
   lazy val tax_calc_token: String = loadConfig("api-gateway.tax_calc_server_token")
   lazy val passthroughHttpHeaders: Seq[String] = configuration.getStringSeq(passthroughHttpHeaderKey).getOrElse(Seq.empty)

   val expiryDecrement: Option[Long] = loadConfigLong("api-gateway.expiry_decrement")
   final val message: String = "Missing required configuration entry for mobile-token-proxy: "

   lazy val ssoUrl: Option[String] = configuration.getString(s"govuk-tax.$env.portal.ssoUrl")

   override def mode: Mode = current.mode
   override def runModeConfiguration: Configuration = current.configuration
}
