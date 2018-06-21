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

package uk.gov.hmrc.mobiletokenproxy.services

import javax.inject.{Inject, Named, Singleton}
import play.api.Logger
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.model.TokenOauthResponse

import scala.concurrent.{ExecutionContext, Future}

trait TokenService {
  def getTokenFromAccessCode(authCode: String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse]

  def getTokenFromRefreshToken(refreshToken: String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse]
}

trait LiveTokenService extends TokenService {
  val genericConnector: GenericConnector
  val pathToAPIGatewayAuthService: String
  val clientId: String
  val redirectUri: String
  val clientSecret: String
  val pathToAPIGatewayTokenService: String
  val expiryDecrement: Long

  def getTokenFromAccessCode(authCode:String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex:ExecutionContext): Future[TokenOauthResponse] = {
    getAPIGatewayToken("code", authCode, "authorization_code", journeyId)
  }

  def getTokenFromRefreshToken(refreshToken:String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse] = {
    getAPIGatewayToken("refresh_token", refreshToken, "refresh_token", journeyId)
  }

  def getAPIGatewayToken(key:String, code: String, grantType:String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex:ExecutionContext): Future[TokenOauthResponse] = {

    val form = Map(
      key -> Seq(code),
      "client_id" -> Seq(clientId),
      "client_secret" -> Seq(clientSecret),
      "grant_type" -> Seq(grantType),
      "redirect_uri" -> Seq(redirectUri)
    )

    def error(message:String, failure:String): Unit = Logger.error(s"Mobile-Token-Proxy - $journeyId - Failed to process request $message. Failure is $failure")

    genericConnector.doPostForm(pathToAPIGatewayTokenService, form).map(result => {
      result.status match {
        case 200 =>
          val accessToken = (result.json \ "access_token").asOpt[String]
          val refreshToken = (result.json \ "refresh_token").asOpt[String]
          val expiresIn = (result.json \ "expires_in").asOpt[Long]

          if (accessToken.isDefined && refreshToken.isDefined && expiresIn.isDefined) {

            TokenOauthResponse(accessToken.get, refreshToken.get, appyDecrementConfig(expiresIn.get))

          } else {
            throw new IllegalArgumentException(s"Failed to read the JSON result attributes from ${result.json}.")
          }

        case _ =>
          throw new RuntimeException(s"Unexpected response code from APIGatewayTokenService: $result.status")
      }
    })
  }

  private def appyDecrementConfig(expiresIn: Long): Long = {
    val expiryDecrementConfig = expiryDecrement
    if(expiryDecrementConfig > expiresIn){
      Logger.error(s"Config error expiry_decrement $expiryDecrementConfig can't be greater than the token expiry ${expiresIn}")
      expiresIn
    } else expiresIn - expiryDecrementConfig
  }

}

@Singleton
class LiveTokenServiceImpl @Inject()(
  override val genericConnector: GenericConnector,
  @Named("api-gateway.pathToAPIGatewayAuthService") override val pathToAPIGatewayAuthService: String,
  @Named("api-gateway.client_id") override val clientId: String,
  @Named("api-gateway.redirect_uri") override val redirectUri: String,
  @Named("api-gateway.client_secret") override val clientSecret: String,
  @Named("api-gateway.pathToAPIGatewayTokenService")  override val pathToAPIGatewayTokenService: String,
  @Named("api-gateway.expiry_decrement") override val expiryDecrement: Long ) extends LiveTokenService {
}
