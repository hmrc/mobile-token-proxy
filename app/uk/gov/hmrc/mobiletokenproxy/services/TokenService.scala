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
import play.api.libs.json._
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobiletokenproxy.config.GoogleAnalyticsConfig
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.model.TokenOauthResponse

import scala.concurrent.{ExecutionContext, Future}


abstract class LocalException extends Exception {
  val apiResponse:Option[ApiFailureResponse]
}

class InvalidAccessCode(response:Option[ApiFailureResponse]) extends LocalException {
  val apiResponse: Option[ApiFailureResponse] = response
}

class FailToRetrieveToken(response:Option[ApiFailureResponse]) extends LocalException {
  val apiResponse: Option[ApiFailureResponse] = response
}

case class ApiFailureResponse(code:String, message:String)

object ApiFailureResponse {
  implicit val format: OFormat[ApiFailureResponse] = Json.format[ApiFailureResponse]
}

trait TokenService {
  def getTokenFromAccessCode(authCode: String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse]

  def getTokenFromRefreshToken(refreshToken: String, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse]
}

trait LiveTokenService extends TokenService {
  val genericConnector: GenericConnector
  val appConfig: GoogleAnalyticsConfig
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

    def unauthorized(message:Option[ApiFailureResponse]) = {
      error(s"Received Refresh Token failure : Status code 400/401", "Token refresh failure")
      throw new InvalidAccessCode(message)
    }

    def retryFailure(message:Option[ApiFailureResponse]) = {
      error(s"API Gateway failure : Status code 503. Client will be requested to retry", "Token refresh failure")
      throw new FailToRetrieveToken(message)
    }

    def generalFailure(message:Option[ApiFailureResponse]) = {
      error(s"General failure : Client will be requested to retry", "Token refresh failure")
      throw new FailToRetrieveToken(message)
    }

    def buildFailureResponse(message:String, generateFailure: Option[ApiFailureResponse] => Nothing) =  {
      val index = message.indexOf("{")
      val indexEnd = message.indexOf("}")
      val body : Option[ApiFailureResponse] = if (index == -1 && indexEnd != -1 && indexEnd > 0)
        None
      else {
        try {
          Json.parse(message.substring(index, indexEnd+1)).asOpt[ApiFailureResponse]
        } catch {
          case ex:Exception =>
            error(s"Failed to parse the JSON response. Exception $ex", "JSON response parse failure")
            None
        }
      }
      generateFailure(body)
    }

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

        case _ => generalFailure(None)
      }
    }).recover {
      case ex: BadRequestException => unauthorized(None)

      case ex: UnauthorizedException => unauthorized(None)

      case ex: ServiceUnavailableException => retryFailure(None)

      // 400 indicates invalid access-token, 401 indicates refresh-token expired.
      case Upstream4xxResponse(message, 400 | 401, _, _) => buildFailureResponse(message, unauthorized)

      case Upstream5xxResponse(message, _ , _) => buildFailureResponse(message, retryFailure)

      case _ => generalFailure(None)
    }
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
                                      override val appConfig: GoogleAnalyticsConfig,
                                      @Named("api-gateway.pathToAPIGatewayAuthService") override val pathToAPIGatewayAuthService: String,
                                      @Named("api-gateway.client_id") override val clientId: String,
                                      @Named("api-gateway.redirect_uri") override val redirectUri: String,
                                      @Named("api-gateway.client_secret") override val clientSecret: String,
                                      @Named("api-gateway.pathToAPIGatewayTokenService")  override val pathToAPIGatewayTokenService: String,
                                      @Named("api-gateway.expiry_decrement") override val expiryDecrement: Long )
  extends LiveTokenService {
}
