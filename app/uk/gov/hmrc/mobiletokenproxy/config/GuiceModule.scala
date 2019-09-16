/*
 * Copyright 2019 HM Revenue & Customs
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

import com.google.inject.name.Names.named
import com.google.inject.{AbstractModule, Provider}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, CryptoWithKeysFromConfig}
import uk.gov.hmrc.mobiletokenproxy.services.{LiveTokenServiceImpl, TokenService}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.util.Try

class GuiceModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  val servicesConfig = new ServicesConfig(configuration, new RunMode(configuration, environment.mode))

  override def configure(): Unit = {
    bindConfigString("google-analytics.token")
    bindConfigString("google-analytics.host")
    bindConfigString("api-gateway.pathToAPIGatewayTokenService")
    bindConfigString("api-gateway.pathToAPIGatewayAuthService")
    bindConfigString("api-gateway.response_type")
    bindConfigString("api-gateway.ngc.scope")
    bindConfigString("api-gateway.ngc.client_id")
    bindConfigString("api-gateway.ngc.redirect_uri")
    bindConfigString("api-gateway.ngc.client_secret")
    bindConfigString("api-gateway.rds.scope")
    bindConfigString("api-gateway.rds.client_id")
    bindConfigString("api-gateway.rds.redirect_uri")
    bindConfigString("api-gateway.rds.client_secret")
    bindConfigLongDefaultToZero("api-gateway.expiry_decrement")
    bind(classOf[HttpVerbs]).to(classOf[WSHttp])
    bind(classOf[TokenService]).to(classOf[LiveTokenServiceImpl])
    bind(classOf[String]).annotatedWith(named("mobile-auth-stub")).toInstance(servicesConfig.baseUrl("mobile-auth-stub"))

    bind(classOf[ProxyPassThroughHttpHeaders]).toInstance(
      new ProxyPassThroughHttpHeaders(
        configuration.getOptional[Seq[String]]("api-gateway.proxyPassthroughHttpHeaders").getOrElse(Seq.empty)))

    bind(classOf[CompositeSymmetricCrypto]).toProvider(
      new Provider[CryptoWithKeysFromConfig] {
        override def get(): CryptoWithKeysFromConfig = new CryptoWithKeysFromConfig("aes", configuration.underlying)
      })
  }

  private def bindConfigLongDefaultToZero(path: String): Unit = {
    val l: Long = Try(configuration.underlying.getLong(path)).getOrElse(0)
    bindConstant().annotatedWith(named(path)).to(l)
  }

  private def bindConfigString(path: String): Unit = {
    bindConstant().annotatedWith(named(path)).to(configuration.underlying.getString(path))
  }
}