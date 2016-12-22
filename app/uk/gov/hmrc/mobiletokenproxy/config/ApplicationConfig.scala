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

package uk.gov.hmrc.mobiletokenproxy.config

import play.api.Play._
import uk.gov.hmrc.play.config.ServicesConfig

trait ApplicationConfig {
    val analyticsToken: Option[String]
    val analyticsHost: String
    val pathToAPIGatewayTokenService:String
    val pathToAPIGatewayAuthService:String
    val client_id:String
    val client_secret:String
    val redirect_uri:String
    val response_type:String
    val scope:String
    val tax_calc_token:String
    val passthroughHttpHeaders:Seq[String]
}

object ApplicationConfig extends ApplicationConfig with ServicesConfig {
  private def loadConfig(key: String) = configuration.getString(key).getOrElse(throw new RuntimeException(s"Missing key: $key"))

  val passthroughHttpHeaderKey = "api-gateway.proxyPassthroughHttpHeaders"

  override lazy val analyticsToken =  Some(loadConfig("google-analytics.token"))
  override lazy val analyticsHost: String = loadConfig("google-analytics.host")

  override lazy val pathToAPIGatewayTokenService = loadConfig("api-gateway.pathToAPIGatewayTokenService")
  override lazy val pathToAPIGatewayAuthService = loadConfig("api-gateway.pathToAPIGatewayAuthService")
  override lazy val scope = loadConfig("api-gateway.scope")
  override lazy val response_type = loadConfig("api-gateway.response_type")
  override lazy val client_id: String = loadConfig("api-gateway.client_id")
  override lazy val redirect_uri: String = loadConfig("api-gateway.redirect_uri")
  override lazy val client_secret: String = loadConfig("api-gateway.client_secret")
  override lazy val tax_calc_token: String = loadConfig("api-gateway.tax_calc_server_token")
  override lazy val passthroughHttpHeaders: Seq[String] = configuration.getStringSeq(passthroughHttpHeaderKey).getOrElse(Seq.empty)

  final val message = "Missing required configuration entry for mobile-token-proxy: "
}
