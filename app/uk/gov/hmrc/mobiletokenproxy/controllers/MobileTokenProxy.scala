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

package uk.gov.hmrc.mobiletokenproxy.controllers

import play.api.Logger
import play.api.libs.json.{JsError, Json}
import uk.gov.hmrc.mobiletokenproxy.config.ApplicationConfig
import uk.gov.hmrc.mobiletokenproxy.connectors._
import uk.gov.hmrc.mobiletokenproxy.model._
import uk.gov.hmrc.mobiletokenproxy.services._
import uk.gov.hmrc.play.frontend.controller.FrontendController
import play.api.mvc._
import uk.gov.hmrc.play.http.HeaderCarrier
import scala.concurrent.{ExecutionContext, Future}


object MobileTokenProxy extends MobileTokenProxy {
  override def genericConnector: GenericConnector = GenericConnector

  override def appConfig = ApplicationConfig

  override val service = LiveTokenService

  override implicit val ec: ExecutionContext = ExecutionContext.global
}

trait MobileTokenProxy extends FrontendController {
  def genericConnector: GenericConnector

  def appConfig: ApplicationConfig

  val service: TokenService

  implicit val ec: ExecutionContext

  def buildTimestamp = System.currentTimeMillis

  def buildResponse(deviceId:String, timestamp:Long, tokenAuthResponse:TokenOauthResponse, id:Option[String]) = {
    val default:Option[String]=None
    val ueid = id.fold(default){recordId => Some(UEID.generateHash(deviceId, recordId, timestamp, appConfig.secret))}
    Json.toJson(TokenResponse(tokenAuthResponse.access_token, ueid, tokenAuthResponse.expires_in))
  }

  def token() = Action.async(BodyParsers.parse.json) { implicit request =>
    request.body.validate[TokenRequest].fold(
      errors => {
        Logger.warn("Received error with service token: " + errors)
        Future.successful(BadRequest(Json.obj("message" -> JsError.toFlatJson(errors))))
      },
      tokenRequest => {
        (tokenRequest.ueid, tokenRequest.authorizationCode) match {

          case (Some(id: String), (Some(authcode: String))) => Future.successful(BadRequest("Only auth-code or ueid can be supplied! Not both!"))

          case (None, Some(authCode: String)) => authCodeRequest(tokenRequest.deviceId, authCode)

          case (Some(ueid: String), None) => refreshRequest(tokenRequest.deviceId,ueid)

          case (None, None) => Future.successful(BadRequest)
          case _ =>
            Logger.error(s"Unknown response from request $tokenRequest")
            Future.successful(InternalServerError)
        }
      })
  }

  private def authCodeRequest(deviceId: String, authCode:String)(implicit hc: HeaderCarrier) = {
    val timestamp = buildTimestamp

    service.getTokenFromAccessCode(authCode, deviceId, timestamp).map(res =>
      Ok(buildResponse(deviceId, timestamp, res._2, Some(res._1)))
    ).recover {
      case e:InvalidAccessCode => Unauthorized

      case e:FailToRetrieveToken => ServiceUnavailable

      case FailToSaveToken(tokenResponse) => Ok(buildResponse(deviceId, timestamp, tokenResponse, None))

      case _ =>
        Logger.error(s"Failed to process the request for $deviceId and $authCode")
        InternalServerError
    }
  }

  private def refreshRequest(deviceId:String, ueid:String)(implicit hc: HeaderCarrier) = {
    service.getTokenFromRefreshToken(deviceId, ueid).map {
      case Some(found) => Ok(buildResponse(deviceId, found.timestamp, found.authToken, Some(found.recordId)))
      case None => NotFound
    }.recover {
      case e:FailToRetrieveToken => ServiceUnavailable

      case FailToSaveToken(tokenResponse) => Ok(buildResponse(deviceId, 0, tokenResponse, None))

      case _ => InternalServerError
    }
  }

}
