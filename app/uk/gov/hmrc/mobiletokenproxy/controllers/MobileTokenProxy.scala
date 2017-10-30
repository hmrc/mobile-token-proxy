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

package uk.gov.hmrc.mobiletokenproxy.controllers

import play.api.Logger
import play.api.libs.json.{JsError, Json}
import play.api.mvc._
import uk.gov.hmrc.crypto.{Crypted, CryptoWithKeysFromConfig, PlainText}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletokenproxy.config.ApplicationConfig
import uk.gov.hmrc.mobiletokenproxy.connectors._
import uk.gov.hmrc.mobiletokenproxy.model._
import uk.gov.hmrc.mobiletokenproxy.services._
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

object MobileTokenProxy extends MobileTokenProxy {
  override def genericConnector: GenericConnector = GenericConnector

  override def appConfig = ApplicationConfig

  override val service = LiveTokenService

  override implicit val ec: ExecutionContext = ExecutionContext.global

  override val aes = CryptoWithKeysFromConfig("aes")
}

trait MobileTokenProxy extends FrontendController {
  def genericConnector: GenericConnector

  def appConfig: ApplicationConfig

  val service: TokenService

  val aes: CryptoWithKeysFromConfig

  implicit val ec: ExecutionContext

  def authorize(journeyId: Option[String] = None) = Action.async { implicit request =>
    val redirectUrl = s"""${appConfig.pathToAPIGatewayAuthService}?client_id=${appConfig.client_id}&redirect_uri=${appConfig.redirect_uri}&scope=${appConfig.scope}&response_type=${appConfig.response_type}"""
    Future.successful(Redirect(redirectUrl).withHeaders(request.headers.toSimpleMap.toSeq :_*))

  }

  def token(journeyId: Option[String] = None) = Action.async(BodyParsers.parse.json) { implicit request =>
    request.body.validate[TokenRequest].fold(
      errors => {
        Logger.warn("Received error with service token: " + errors)
        Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors))))
      },
      tokenRequest => {

        def buildHeaderCarrier = {
          val headers: scala.collection.immutable.Map[String, String] = request.headers.toSimpleMap.filter {
            a => appConfig.passthroughHttpHeaders.exists(b => b.compareToIgnoreCase(a._1) == 0)
          }
          hc.withExtraHeaders(headers.toSeq: _*)
        }

        (tokenRequest.refreshToken, tokenRequest.authorizationCode) match {

          case (Some(refreshToken: String), (Some(authcode: String))) =>
            Future.successful(BadRequest("Only authorizationCode or refreshToken can be supplied! Not both!"))

          case (None, Some(authCode: String)) =>
            getToken(service.getTokenFromAccessCode(authCode, journeyId)(buildHeaderCarrier, ec))

          case (Some(refreshToken: String), None) =>
            getToken(service.getTokenFromRefreshToken(refreshToken, journeyId)(buildHeaderCarrier, ec))

          case _ =>
            Future.successful(BadRequest)
        }
      })
  }

  def taxcalctoken(journeyId: Option[String] = None) = Action.async { implicit request =>
    val encrypted: Crypted = aes.encrypt(PlainText(appConfig.tax_calc_token))
    Future.successful(Ok(Json.toJson(TokenResponse(encrypted.value))))
  }

  private def getToken(func: => Future[TokenOauthResponse])(implicit hc: HeaderCarrier) = {
    func.map { res => Ok(Json.toJson(res))
    }.recover {
      recoverError
    }
  }

  private def recoverError: scala.PartialFunction[scala.Throwable, Result] = {
    case ex: FailToRetrieveToken => buildFailureResponse(ServiceUnavailable, ex.apiResponse)

    case ex: InvalidAccessCode => buildFailureResponse(Unauthorized, ex.apiResponse)

    case _ => ServiceUnavailable
  }

  private def buildFailureResponse(statusResponse:Status, apiResponse:Option[ApiFailureResponse]) =
    apiResponse.fold(statusResponse("")){ resp => statusResponse(Json.toJson(resp)) }
}
