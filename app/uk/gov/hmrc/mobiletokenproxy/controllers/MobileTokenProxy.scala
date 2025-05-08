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
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UpstreamErrorResponse}
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
  @Named("api-gateway.ngc-test.client_id") ngcTestClientId:                      String,
  @Named("api-gateway.ngc-test.scope") ngcTestScope:                             String,
  @Named("api-gateway.ngc-test.redirect_uri") ngcTestRedirectUri:                String,
  @Named("api-gateway.ngc.v2.client_id") ngcClientIdV2:                          String,
  @Named("api-gateway.ngc.v2.scope") ngcScopeV2:                                 String,
  @Named("api-gateway.ngc.v2.redirect_uri") ngcRedirectUriV2:                    String,
  @Named("api-gateway.ngc-test.v2.client_id") ngcTestClientIdV2:                 String,
  @Named("api-gateway.ngc-test.v2.scope") ngcTestScopeV2:                        String,
  @Named("api-gateway.ngc-test.v2.redirect_uri") ngcTestRedirectUriV2:           String,
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
      case "ngc-test" =>
        s"$pathToAPIGatewayAuthService?client_id=$ngcTestClientId&redirect_uri=$ngcTestRedirectUri&scope=$ngcTestScope&response_type=$responseType"
      case _ => throw new IllegalArgumentException("Invalid service id")
    }
    Future.successful(Redirect(redirectUrl).withHeaders(request.headers.toSimpleMap.toSeq: _*))
  }

  def authorizeV2(
    journeyId: JourneyId,
    userAgent: String,
    serviceId: String
  ): Action[AnyContent] = Action.async { implicit request =>
    val redirectUrl = serviceId.toLowerCase match {
      case "ngc" =>
        s"$pathToAPIGatewayAuthService?client_id=$ngcClientIdV2&redirect_uri=$ngcRedirectUriV2&scope=$ngcScopeV2&response_type=$responseType"
      case _ =>
        s"$pathToAPIGatewayAuthService?client_id=$ngcTestClientIdV2&redirect_uri=$ngcTestRedirectUriV2&scope=$ngcTestScopeV2&response_type=$responseType"
    }
    Future.successful(
      Redirect(redirectUrl).withHeaders((request.headers.toSimpleMap + ("User-Agent" -> userAgent)).toSeq: _*)
    )
  }

  def token(
    journeyId: JourneyId,
    serviceId: String = "ngc"
  ): Action[JsValue] =
    if (serviceId == "ngc") {
      Action.async(controllerComponents.parsers.json) { implicit request =>
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
                  getToken(
                    service.getTokenFromAccessCode(authCode, journeyId, v2 = false, serviceId)(buildHeaderCarrier, ec)
                  )

                case (Some(refreshToken: String), None) =>
                  getToken(
                    service.getTokenFromRefreshToken(refreshToken, journeyId, v2 = false, serviceId)(buildHeaderCarrier,
                                                                                                     ec)
                  )

                case _ =>
                  Future.successful(BadRequest)
              }
            }
          )
      }
    } else {
      Action.async(controllerComponents.parsers.json) { implicit request =>
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
                  getToken(
                    service.getTokenFromAccessCode(authCode, journeyId, v2 = false, serviceId)(buildHeaderCarrier, ec)
                  )

                case (Some(refreshToken: String), None) =>
                  getToken(
                    service.getTokenFromRefreshToken(refreshToken, journeyId, v2 = false, serviceId)(buildHeaderCarrier,
                                                                                                     ec)
                  )

                case _ =>
                  Future.successful(BadRequest)
              }
            }
          )
      }
    }

  def tokenV2(
    journeyId: JourneyId,
    serviceId: String
  ): Action[Map[String, Seq[String]]] =
    if (serviceId == "ngc") {
      Action.async(parse.formUrlEncoded) { implicit form: MessagesRequest[Map[String, Seq[String]]] =>
        def buildHeaderCarrier = {
          val headers: Map[String, String] = form.headers.toSimpleMap.filter { a =>
            proxyPassthroughHttpHeaders.exists(b => b.compareToIgnoreCase(a._1) == 0)
          }
          hc.withExtraHeaders(headers.toSeq: _*)
        }

        (form.body.get("refreshToken"), form.body.get("authorizationCode")) match {

          case (Some(_: Seq[String]), Some(_: Seq[String])) =>
            Future.successful(BadRequest("Only authorizationCode or refreshToken can be supplied! Not both!"))

          case (None, Some(authCode: Seq[String])) =>
            getToken(
              service.getTokenFromAccessCode(
                authCode.headOption.getOrElse(throw new BadRequestException("auth token not found")),
                journeyId,
                v2 = true,
                serviceId
              )(buildHeaderCarrier, ec)
            )

          case (Some(refreshToken: Seq[String]), None) =>
            getToken(
              service.getTokenFromRefreshToken(
                refreshToken.headOption.getOrElse(throw new BadRequestException("refresh token not found")),
                journeyId,
                v2 = true,
                serviceId
              )(buildHeaderCarrier, ec)
            )

          case _ =>
            Future.successful(BadRequest)
        }

      }
    } else {
      Action.async(parse.formUrlEncoded) { implicit form: MessagesRequest[Map[String, Seq[String]]] =>
        def buildHeaderCarrier = {
          val headers: Map[String, String] = form.headers.toSimpleMap.filter { a =>
            proxyPassthroughHttpHeaders.exists(b => b.compareToIgnoreCase(a._1) == 0)
          }
          hc.withExtraHeaders(headers.toSeq: _*)
        }

        (form.body.get("refreshToken"), form.body.get("authorizationCode")) match {

          case (Some(_: Seq[String]), Some(_: Seq[String])) =>
            Future.successful(BadRequest("Only authorizationCode or refreshToken can be supplied! Not both!"))

          case (None, Some(authCode: Seq[String])) =>
            getToken(
              service.getTokenFromAccessCode(
                authCode.headOption.getOrElse(throw new BadRequestException("auth token not found")),
                journeyId,
                v2 = true,
                serviceId
              )(buildHeaderCarrier, ec)
            )

          case (Some(refreshToken: Seq[String]), None) =>
            getToken(
              service.getTokenFromRefreshToken(
                refreshToken.headOption.getOrElse(throw new BadRequestException("refresh token not found")),
                journeyId,
                v2 = true,
                serviceId
              )(buildHeaderCarrier, ec)
            )

          case _ =>
            Future.successful(BadRequest)
        }

      }

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
    case UpstreamErrorResponse(_, 400, _, _) => Unauthorized
    case UpstreamErrorResponse(_, 401, _, _) => Unauthorized
    case UpstreamErrorResponse(_, 403, _, _) => Forbidden
    case _                                   => InternalServerError
  }
}
