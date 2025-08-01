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

package uk.gov.hmrc.mobiletokenproxy.services

import javax.inject.{Inject, Named, Singleton}
import play.api.Logger
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, UNAUTHORIZED}
import uk.gov.hmrc.http.*
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.model.TokenOauthResponse
import uk.gov.hmrc.mobiletokenproxy.types.JourneyId

import scala.concurrent.{ExecutionContext, Future}

trait TokenService {

  def getTokenFromAccessCode(
    authCode: String,
    journeyId: JourneyId,
    v2: Boolean,
    serviceId: String = "ngc"
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse]

  def getTokenFromRefreshToken(
    refreshToken: String,
    journeyId: JourneyId,
    v2: Boolean,
    serviceId: String = "ngc"
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse]

}

trait LiveTokenService extends TokenService {
  val genericConnector: GenericConnector
  val pathToAPIGatewayAuthService: String
  val pathToAPIGatewayTokenService: String
  val expiryDecrement: Long
  val ngcClientId: String
  val ngcRedirectUri: String
  val ngcClientSecret: String
  val ngcClientIdV2: String
  val ngcRedirectUriV2: String
  val ngcClientIdTest: String
  val ngcClientSecretTest: String
  val ngcRedirectUriTest: String
  val ngcClientIdV2Test: String
  val ngcRedirectUriV2Test: String

  def getTokenFromAccessCode(
    authCode: String,
    journeyId: JourneyId,
    v2: Boolean,
    serviceId: String
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse] =
    if (serviceId == "ngc") {
      getAPIGatewayToken("code", authCode, "authorization_code", journeyId, v2)
    } else {
      getAPIGatewayTokenTest("code", authCode, "authorization_code", journeyId, v2)
    }

  def getTokenFromRefreshToken(
    refreshToken: String,
    journeyId: JourneyId,
    v2: Boolean,
    serviceId: String
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse] =
    if (serviceId == "ngc")
      getAPIGatewayToken("refresh_token", refreshToken, "refresh_token", journeyId, v2)
    else
      getAPIGatewayTokenTest("refresh_token", refreshToken, "refresh_token", journeyId, v2)

  def getAPIGatewayToken(
    key: String,
    code: String,
    grantType: String,
    journeyId: JourneyId,
    v2: Boolean
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse] = {

    val form = {
      if (v2) {
        Map(
          key             -> Seq(code),
          "client_id"     -> Seq(ngcClientIdV2),
          "client_secret" -> Seq(ngcClientSecret),
          "grant_type"    -> Seq(grantType),
          "redirect_uri"  -> Seq(ngcRedirectUriV2)
        )
      } else {
        Map(
          key             -> Seq(code),
          "client_id"     -> Seq(ngcClientId),
          "client_secret" -> Seq(ngcClientSecret),
          "grant_type"    -> Seq(grantType),
          "redirect_uri"  -> Seq(ngcRedirectUri)
        )
      }

    }

    genericConnector
      .doPostForm(pathToAPIGatewayTokenService, form)
      .map { result =>
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

          case 400 =>
            throw UpstreamErrorResponse(s"BAD REQUEST from APIGatewayTokenService: ${result.status}", BAD_REQUEST, BAD_REQUEST, Map.empty)
          case 401 =>
            throw UpstreamErrorResponse(s"UNAUTHORIZED Request from APIGatewayTokenService: ${result.status}", UNAUTHORIZED, UNAUTHORIZED, Map.empty)
          case 403 =>
            throw UpstreamErrorResponse(s"FORBIDDEN Request from APIGatewayTokenService: ${result.status}", FORBIDDEN, FORBIDDEN, Map.empty)
          case _ =>
            throw new RuntimeException(s"Unexpected response code from APIGatewayTokenService: $result.status")
        }
      }
  }

  def getAPIGatewayTokenTest(
    key: String,
    code: String,
    grantType: String,
    journeyId: JourneyId,
    v2: Boolean
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse] = {

    val form = {
      if (v2) {
        Map(
          key             -> Seq(code),
          "client_id"     -> Seq(ngcClientIdV2Test),
          "client_secret" -> Seq(ngcClientSecretTest),
          "grant_type"    -> Seq(grantType),
          "redirect_uri"  -> Seq(ngcRedirectUriV2Test)
        )
      } else {
        Map(
          key             -> Seq(code),
          "client_id"     -> Seq(ngcClientIdTest),
          "client_secret" -> Seq(ngcClientSecretTest),
          "grant_type"    -> Seq(grantType),
          "redirect_uri"  -> Seq(ngcRedirectUriTest)
        )
      }

    }

    genericConnector
      .doPostForm(pathToAPIGatewayTokenService, form)
      .map { result =>
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

          case 400 =>
            throw UpstreamErrorResponse(s"BAD REQUEST from APIGatewayTokenService: ${result.status}", BAD_REQUEST, BAD_REQUEST, Map.empty)
          case 401 =>
            throw UpstreamErrorResponse(s"UNAUTHORIZED Request from APIGatewayTokenService: ${result.status}", UNAUTHORIZED, UNAUTHORIZED, Map.empty)
          case 403 =>
            throw UpstreamErrorResponse(s"FORBIDDEN Request from APIGatewayTokenService: ${result.status}", FORBIDDEN, FORBIDDEN, Map.empty)
          case _ =>
            throw new RuntimeException(s"Unexpected response code from APIGatewayTokenService: $result.status")
        }
      }
  }

  private def appyDecrementConfig(expiresIn: Long): Long = {
    val expiryDecrementConfig = expiryDecrement
    val logger: Logger = Logger(this.getClass)
    if (expiryDecrementConfig > expiresIn) {
      logger.error(
        s"Config error expiry_decrement $expiryDecrementConfig can't be greater than the token expiry $expiresIn"
      )
      expiresIn
    } else expiresIn - expiryDecrementConfig
  }

}

@Singleton
class LiveTokenServiceImpl @Inject() (override val genericConnector: GenericConnector,
                                      @Named("api-gateway.pathToAPIGatewayAuthService") override val pathToAPIGatewayAuthService: String,
                                      @Named("api-gateway.pathToAPIGatewayTokenService") override val pathToAPIGatewayTokenService: String,
                                      @Named("api-gateway.expiry_decrement") override val expiryDecrement: Long,
                                      @Named("api-gateway.ngc.client_id") override val ngcClientId: String,
                                      @Named("api-gateway.ngc.redirect_uri") override val ngcRedirectUri: String,
                                      @Named("api-gateway.ngc.client_secret") override val ngcClientSecret: String,
                                      @Named("api-gateway.ngc.v2.client_id") override val ngcClientIdV2: String,
                                      @Named("api-gateway.ngc.v2.redirect_uri") override val ngcRedirectUriV2: String,
                                      @Named("api-gateway.ngc-test.client_secret") override val ngcClientIdTest: String,
                                      @Named("api-gateway.ngc-test.client_secret") override val ngcClientSecretTest: String,
                                      @Named("api-gateway.ngc-test.redirect_uri") override val ngcRedirectUriTest: String,
                                      @Named("api-gateway.ngc-test.v2.client_id") override val ngcClientIdV2Test: String,
                                      @Named("api-gateway.ngc-test.v2.redirect_uri") override val ngcRedirectUriV2Test: String
                                     )
    extends LiveTokenService {}
