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

package uk.gov.hmrc.mobiletokenproxy.service

import eu.timepit.refined.auto.*
import org.scalamock.scalatest.MockFactory
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsValue
import play.api.libs.json.Json.parse
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.*
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.model.TokenOauthResponse
import uk.gov.hmrc.mobiletokenproxy.services.LiveTokenServiceImpl
import uk.gov.hmrc.mobiletokenproxy.types.JourneyId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.reflect.ClassTag

class TokenServiceSpec extends PlaySpec with MockFactory with ScalaFutures {
  val connector: GenericConnector = mock[GenericConnector]

  val pathToAPIGatewayTokenService: String = "http://localhost:8236/oauth/token"
  val ngcClientId: String = "client_id"
  val ngcRedirectUri: String = "redirect_uri"
  val ngcClientSecret: String = "client_secret"
  val pathToAPIGatewayAuthService: String = "http://localhost:8236/oauth/authorize"
  val expiryDecrement: Long = 0
  val accessToken: String = "495b5b1725d590eb87d0f6b7dcea32a9"
  val refreshToken: String = "b75f2ed960898b4cd38f23934c6befb2"
  val expiresIn: Int = 14400
  val journeyId: JourneyId = JourneyId.from("dd1ebd2e-7156-47c7-842b-8308099c5e75").toOption.get
  val defaultServiceId: String = "ngc"
  val ngcClientIdV2: String = "client_id_v2"
  val ngcRedirectUriV2: String = "redirect_uri_v2"
  val ngcClientIdTest: String = "ngc_client_id_test"
  val ngcRedirectUriTest: String = "ngc_redirect_uri_test"
  val ngcClientSecretTest: String = "ngc_client_secretId_test"
  val ngcClientIdV2Test: String = "ngc_clientId_v2_test"
  val ngcRedirectUriV2Test: String = "ngc_redirect_uri_v2_test"

  val service: LiveTokenServiceImpl = new LiveTokenServiceImpl(
    genericConnector             = connector,
    pathToAPIGatewayAuthService  = pathToAPIGatewayAuthService,
    pathToAPIGatewayTokenService = pathToAPIGatewayTokenService: String,
    expiryDecrement              = expiryDecrement,
    ngcClientId                  = ngcClientId,
    ngcRedirectUri               = ngcRedirectUri,
    ngcClientSecret              = ngcClientSecret,
    ngcClientIdV2                = ngcClientIdV2,
    ngcRedirectUriV2             = ngcRedirectUriV2,
    ngcClientIdTest              = ngcClientIdTest,
    ngcClientSecretTest          = ngcClientSecretTest,
    ngcRedirectUriTest           = ngcRedirectUriTest,
    ngcClientIdV2Test            = ngcClientIdV2Test,
    ngcRedirectUriV2Test         = ngcRedirectUriV2Test
  )

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  "getTokenFromAccessCode" should {
    val authCode = "authCode"

    val form = Map(
      "code"          -> Seq(authCode),
      "client_id"     -> Seq(ngcClientId),
      "client_secret" -> Seq(ngcClientSecret),
      "grant_type"    -> Seq("authorization_code"),
      "redirect_uri"  -> Seq(ngcRedirectUri)
    )

    "return a token" in {
      val tokenResponseFromAuthorizationCode: String =
        s"""{ "access_token": "$accessToken", "refresh_token": "$refreshToken", "expires_in": $expiresIn }"""
      val response = HttpResponse(200, tokenResponseFromAuthorizationCode)

      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(response))

      val tokenResponse: TokenOauthResponse =
        await(service.getTokenFromAccessCode(authCode, journeyId, v2 = false))

      tokenResponse.access_token mustBe accessToken
      tokenResponse.refresh_token mustBe refreshToken
      tokenResponse.expires_in mustBe expiresIn
    }

    "throw an exception if access_token is not returned" in {
      handleInvalidResponseJson(parse(s"""{ "refresh_token": "$refreshToken", "expires_in": $expiresIn }"""))
    }

    "throw an exception if refresh_token is not returned" in {
      handleInvalidResponseJson(parse(s"""{ "access_token": "$accessToken", "expires_in": $expiresIn }"""))
    }

    "throw an exception if expires_in is not returned" in {
      handleInvalidResponseJson(parse(s"""{ "access_token": "$accessToken", "refresh_token": "$refreshToken" }"""))
    }

    "not swallow exceptions" in {
      propagateException(new BadRequestException("I don't understand!"))
      propagateException(new UnauthorizedException("Denied!"))
      propagateException(new ServiceUnavailableException("Sorry we cannot take your call right now!"))
      propagateException(UpstreamErrorResponse("4xx", 400, 400))
      propagateException(UpstreamErrorResponse("5xx", 500, 500))
      propagateException(new Exception())
    }

    "handle the exception when APIGatewayTokenService returns a code other than 200" in {
      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(HttpResponse(201, "{}")))

      intercept[RuntimeException] {
        service.getTokenFromAccessCode(authCode, journeyId, v2 = false).futureValue
      }
    }

    def handleInvalidResponseJson(tokenResponseFromAuthorizationCode: JsValue) = {
      val response = HttpResponse(200, tokenResponseFromAuthorizationCode.toString())

      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(response))

      intercept[RuntimeException] {
        service.getTokenFromAccessCode(authCode, journeyId, v2 = false).futureValue
      }
    }

    def propagateException[T <: Exception](exception: T)(implicit ct: ClassTag[T]): Assertion = {
      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.failed(exception))

      val actual = intercept[T] {
        Await.result(service.getTokenFromAccessCode(authCode, journeyId, v2 = false), 10 seconds)
      }
      exception mustBe actual
    }
  }

  "getTokenFromRefreshToken" should {
    val refreshToken = "refresh_token"

    val form = Map(
      refreshToken    -> Seq(refreshToken),
      "client_id"     -> Seq(ngcClientId),
      "client_secret" -> Seq(ngcClientSecret),
      "grant_type"    -> Seq(refreshToken),
      "redirect_uri"  -> Seq(ngcRedirectUri)
    )

    "return a token" in {
      val tokenResponseFromAuthorizationCode: String =
        s"""{ "access_token": "$accessToken", "refresh_token": "$refreshToken", "expires_in": $expiresIn }"""
      val response = HttpResponse(200, tokenResponseFromAuthorizationCode)

      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(response))

      val tokenResponse: TokenOauthResponse =
        service.getTokenFromRefreshToken(refreshToken, journeyId, v2 = false).futureValue

      tokenResponse.access_token mustBe accessToken
      tokenResponse.refresh_token mustBe refreshToken
      tokenResponse.expires_in mustBe expiresIn
    }

    "throw FailToRetrieveToken if access_token is not returned" in {
      handleInvalidResponseJson(parse(s"""{ "refresh_token": "$refreshToken", "expires_in": $expiresIn }"""))
    }

    "throw FailToRetrieveToken if refresh_token is not returned" in {
      handleInvalidResponseJson(parse(s"""{ "access_token": "$accessToken", "expires_in": $expiresIn }"""))
    }

    "throw FailToRetrieveToken if expires_in is not returned" in {
      handleInvalidResponseJson(parse(s"""{ "access_token": "$accessToken", "refresh_token": "$refreshToken" }"""))
    }

    "not swallow exceptions" in {
      propagateException(new BadRequestException("I should be in detention"))
      propagateException(new UnauthorizedException("Oh no you don't"))
      propagateException(new ServiceUnavailableException("Sorry we cannot take your call right now"))
      propagateException(UpstreamErrorResponse("4xx", 400, 400))
      propagateException(UpstreamErrorResponse("5xx", 500, 500))
      propagateException(new Exception())
    }

    "handle the exception when APIGatewayTokenService returns a code other than 200" in {
      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(HttpResponse(201, "{}")))

      intercept[RuntimeException] {
        service.getTokenFromRefreshToken(refreshToken, journeyId, v2 = false).futureValue
      }
    }

    def handleInvalidResponseJson(tokenResponseFromAuthorizationCode: JsValue) = {
      val response = HttpResponse(200, tokenResponseFromAuthorizationCode.toString())

      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(response))

      intercept[RuntimeException] {
        service.getTokenFromRefreshToken(refreshToken, journeyId, v2 = false).futureValue
      }
    }

    def propagateException(exception: Exception) = {
      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.failed(exception))

      val actual = intercept[Exception] {
        Await.result(service.getTokenFromRefreshToken(refreshToken, journeyId, v2 = false), 10 seconds)
      }
      exception mustBe actual
    }
  }
}
