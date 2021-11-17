/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util.UUID

import com.google.inject.name.Named
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.mobiletokenproxy.model._
import uk.gov.hmrc.mobiletokenproxy.types.ModelTypes.JourneyId
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SandboxMobileTokenProxy @Inject() (
  @Named("mobile-auth-stub") mobileAuthStubUrl: String,
  cc:                                           MessagesControllerComponents)
    extends FrontendController(cc) {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val logger: Logger = Logger(this.getClass)

  def token(journeyId: JourneyId): Action[JsValue] = Action.async(controllerComponents.parsers.json) { implicit request =>
    request.body
      .validate[TokenRequest]
      .fold(
        errors => {
          logger.warn("Received error with service token: " + errors)
          Future successful BadRequest(Json.obj("message" -> JsError.toJson(errors)))
        },
        tokenRequest =>
          (tokenRequest.refreshToken, tokenRequest.authorizationCode) match {

            case (Some(_), Some(_)) =>
              Future successful BadRequest("Only authorizationCode or refreshToken can be supplied! Not both!")

            case (None, Some(_)) =>
              Future successful Ok(
                Json.toJson(TokenOauthResponse(UUID.randomUUID().toString, UUID.randomUUID().toString, 13800))
              )
            case (Some(_), None) =>
              Future successful Ok(
                Json.toJson(TokenOauthResponse(UUID.randomUUID().toString, UUID.randomUUID().toString, 13800))
              )

            case _ =>
              Future successful BadRequest
          }
      )
  }

  def authorize(journeyId: JourneyId): Action[AnyContent] = Action.async { implicit request =>
    Future successful Redirect(s"$mobileAuthStubUrl/gg/sign-in")
  }
}
