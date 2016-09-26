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

package uk.gov.hmrc.mobiletokenproxy.services

import play.api.Logger
import play.api.Play._
import play.api.libs.json._
import uk.gov.hmrc.mobiletokenproxy.config.ApplicationConfig
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.model.TokenOauthResponse
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


class InvalidAccessCode extends Exception

class FailToRetrieveToken extends Exception

case class UpdateRefreshToken(deviceId:String, refreshToken: String)

object UpdateRefreshToken {
  implicit val format = Json.format[UpdateRefreshToken]
}

trait TokenService {
  def getTokenFromAccessCode(authCode: String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse]

  def getTokenFromRefreshToken(refreshToken: String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse]
}

trait LiveTokenService extends TokenService {
  def genericConnector: GenericConnector
  def appConfig: ApplicationConfig

  def getConfig(key:String) = current.configuration.getString(key).getOrElse(throw new IllegalArgumentException(s"Failed to resolve $key"))

  def getTokenFromAccessCode(authCode:String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex:ExecutionContext): Future[TokenOauthResponse] = {
    getAPIGatewayToken("code", authCode)
  }

  def getTokenFromRefreshToken(refreshToken:String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse] = {
    getAPIGatewayToken("refresh_token", refreshToken, journeyId)
  }

  def getAPIGatewayToken(key:String, code: String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex:ExecutionContext): Future[TokenOauthResponse] = {

    val form = Map(
      key -> Seq(code),
      "client_id" -> Seq(appConfig.client_id),
      "client_secret" -> Seq(appConfig.client_secret),
      "grant_type" -> Seq(appConfig.grant_type),
      "redirect_uri" -> Seq(appConfig.redirect_uri)
    )

    def error(message:String, failure:String) = Logger.error(s"Mobile-Token-Proxy - $journeyId - Failed to process request $message. Failure is $failure")

    genericConnector.doPostForm(appConfig.pathToAPIGatewayTokenService, form).map(result => {
      result.status match {
        case 200 =>
          val accessToken = (result.json \ "access_token").asOpt[String]
          val refreshToken = (result.json \ "refresh_token").asOpt[String]
          val expiresIn = (result.json \ "expires_in").asOpt[Long]

          if (accessToken.isDefined && refreshToken.isDefined && expiresIn.isDefined) {
            TokenOauthResponse(accessToken.get, refreshToken.get, expiresIn.get)
          } else {
            throw new IllegalArgumentException(s"Failed to read the JSON result attributes from ${result.json}.")
          }

        case 400 | 401 => // Force the user to login again! Token supplied has already been used (400) or invalid (401).
          error(s"Received Refresh Token failure : Status code ${result.status}", "Token refresh failure")
          throw new InvalidAccessCode

        case 503 => // Indicates the API gateway failed to process the request and should be re-tried.
          error(s"API Gateway failure : Status code ${result.status}. Client will be requested to retry", "Token refresh failure")
          throw new FailToRetrieveToken

        case _ => // General failure indicates the API gateway failed to process the request and should be re-tried.
          error(s"General failure : Status code ${result.status}. Client will be requested to retry", "Token refresh failure")
          throw new FailToRetrieveToken
      }
    })
  }

}

object LiveTokenService extends LiveTokenService {
  override def genericConnector: GenericConnector = GenericConnector
  override def appConfig = ApplicationConfig
}
