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

package uk.gov.hmrc.mobiletokenproxy.controllers

import com.google.inject.Provider
import javax.inject.{Inject, Named, Singleton}
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, PlainText}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletokenproxy.config.ProxyPassThroughHttpHeaders
import uk.gov.hmrc.mobiletokenproxy.connectors._
import uk.gov.hmrc.mobiletokenproxy.model._
import uk.gov.hmrc.mobiletokenproxy.services._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MobileTokenProxy @Inject()(
  genericConnector: GenericConnector,
  service: TokenService,
  cryptoProvider: Provider[CompositeSymmetricCrypto],
  proxyPassthroughHttpHeaders: ProxyPassThroughHttpHeaders,
  @Named("api-gateway.pathToAPIGatewayAuthService") pathToAPIGatewayAuthService: String,
  @Named("api-gateway.client_id") clientId: String,
  @Named("api-gateway.redirect_uri") redirectUri: String,
  @Named("api-gateway.scope") scope: String,
  @Named("api-gateway.response_type") responseType: String,
  @Named("api-gateway.tax_calc_server_token") taxCalcServerToken: String)
  extends FrontendController {
  implicit val ec: ExecutionContext = ExecutionContext.global

  lazy val aesCryptographer: CompositeSymmetricCrypto = cryptoProvider.get()


  def authorize(journeyId: Option[String] = None): Action[AnyContent] = Action.async { implicit request =>
    val redirectUrl = s"$pathToAPIGatewayAuthService?client_id=$clientId&redirect_uri=$redirectUri&scope=$scope&response_type=$responseType"
    Future.successful(Redirect(redirectUrl).withHeaders(request.headers.toSimpleMap.toSeq :_*))
  }

  def token(journeyId: Option[String] = None): Action[JsValue] = Action.async(BodyParsers.parse.json) { implicit request =>
    request.body.validate[TokenRequest].fold(
      errors => {
        Logger.warn("Received error with service token: " + errors)
        Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors))))
      },
      tokenRequest => {

        def buildHeaderCarrier = {
          val headers: Map[String, String] = request.headers.toSimpleMap.filter {
            a => proxyPassthroughHttpHeaders.exists(b => b.compareToIgnoreCase(a._1) == 0)
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

  def taxcalctoken(journeyId: Option[String] = None): Action[AnyContent] = Action.async { implicit request =>
    val encrypted: Crypted = aesCryptographer.encrypt(PlainText(taxCalcServerToken))
    Future.successful(Ok(toJson(TokenResponse(encrypted.value))))
  }

  private def getToken(func: => Future[TokenOauthResponse])(implicit hc: HeaderCarrier): Future[Result] = {
    func.map { res => Ok(toJson(res))
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
    apiResponse.fold(statusResponse("")){ resp => statusResponse(toJson(resp)) }
}
