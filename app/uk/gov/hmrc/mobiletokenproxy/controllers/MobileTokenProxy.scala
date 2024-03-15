/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Named, Singleton}
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, Upstream4xxResponse}
import uk.gov.hmrc.mobiletokenproxy.config.ProxyPassThroughHttpHeaders
import uk.gov.hmrc.mobiletokenproxy.model._
import uk.gov.hmrc.mobiletokenproxy.services._
import uk.gov.hmrc.mobiletokenproxy.types.ModelTypes.JourneyId
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MobileTokenProxy @Inject() (
  service:                                                                       TokenService,
  proxyPassthroughHttpHeaders:                                                   ProxyPassThroughHttpHeaders,
  @Named("api-gateway.response_type") responseType:                              String,
  @Named("api-gateway.pathToAPIGatewayAuthService") pathToAPIGatewayAuthService: String,
  @Named("api-gateway.ngc.client_id") ngcClientId:                               String,
  @Named("api-gateway.ngc.scope") ngcScope:                                      String,
  @Named("api-gateway.ngc.redirect_uri") ngcRedirectUri:                         String,
  messagesControllerComponents:                                                  MessagesControllerComponents
)(implicit ec:                                                                   ExecutionContext)
    extends FrontendController(messagesControllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def authorize(
    journeyId: JourneyId,
    serviceId: String = "ngc"
  ): Action[AnyContent] = Action.async { implicit request =>
    val redirectUrl = serviceId.toLowerCase match {
      case "ngc" =>
        s"$pathToAPIGatewayAuthService?client_id=$ngcClientId&redirect_uri=$ngcRedirectUri&scope=$ngcScope&response_type=$responseType"
      case _ => throw new IllegalArgumentException("Invalid service id")
    }
    Future.successful(Redirect(redirectUrl).withHeaders(request.headers.toSimpleMap.toSeq: _*))
  }

  def token(
    journeyId: JourneyId,
    serviceId: String = "ngc"
  ): Action[JsValue] = Action.async(controllerComponents.parsers.json) { implicit request =>
    request.body
      .validate[TokenRequest]
      .fold(
        errors => {
          logger.warn("Received error with service token: " + errors)
          Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors))))
        },
        tokenRequest => {

          def buildHeaderCarrier = {
            val headers: Map[String, String] = request.headers.toSimpleMap.filter { a =>
              proxyPassthroughHttpHeaders.exists(b => b.compareToIgnoreCase(a._1) == 0)
            }
            hc.withExtraHeaders(headers.toSeq: _*)
          }

          (tokenRequest.refreshToken, tokenRequest.authorizationCode) match {

            case (Some(_: String), Some(authcode: String)) =>
              Future.successful(BadRequest("Only authorizationCode or refreshToken can be supplied! Not both!"))

            case (None, Some(authCode: String)) =>
              getToken(service.getTokenFromAccessCode(authCode, journeyId, serviceId)(buildHeaderCarrier, ec))

            case (Some(refreshToken: String), None) =>
              getToken(service.getTokenFromRefreshToken(refreshToken, journeyId, serviceId)(buildHeaderCarrier, ec))

            case _ =>
              Future.successful(BadRequest)
          }
        }
      )
  }

  private def getToken(func: => Future[TokenOauthResponse])(implicit hc: HeaderCarrier): Future[Result] =
    func
      .map { res =>
        Ok(toJson(res))
      }
      .recover {
        recoverError
      }

  private def recoverError: scala.PartialFunction[scala.Throwable, Result] = {
    case _: BadRequestException => Unauthorized
    case Upstream4xxResponse(_, 401, _, _) => Unauthorized
    case Upstream4xxResponse(_, 403, _, _) => Forbidden
    case _                                 => InternalServerError
  }
}
