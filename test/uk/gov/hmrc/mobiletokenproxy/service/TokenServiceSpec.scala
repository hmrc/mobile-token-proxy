/*
 * Copyright 2019 HM Revenue & Customs
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

import org.scalamock.scalatest.MockFactory
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsValue
import play.api.libs.json.Json.parse
import uk.gov.hmrc.http.{BadRequestException, _}
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.model.TokenOauthResponse
import uk.gov.hmrc.mobiletokenproxy.services.LiveTokenServiceImpl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.reflect.ClassTag

class TokenServiceSpec extends PlaySpec with MockFactory with ScalaFutures {
  val connector: GenericConnector = mock[GenericConnector]

  val pathToAPIGatewayTokenService: String = "http://localhost:8236/oauth/token"
  val clientId:                     String = "client_id"
  val redirectUri:                  String = "redirect_uri"
  val clientSecret:                 String = "client_secret"
  val pathToAPIGatewayAuthService:  String = "http://localhost:8236/oauth/authorize"
  val expiryDecrement:              Long   = 0
  val accessToken  = "495b5b1725d590eb87d0f6b7dcea32a9"
  val refreshToken = "b75f2ed960898b4cd38f23934c6befb2"
  val expiresIn    = 14400

  val service: LiveTokenServiceImpl = new LiveTokenServiceImpl(
    connector,
    pathToAPIGatewayAuthService,
    clientId: String,
    redirectUri,
    clientSecret,
    pathToAPIGatewayTokenService,
    expiryDecrement)

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  "getTokenFromAccessCode" should {
    val authCode = "authCode"

    val form = Map(
      "code"          -> Seq(authCode),
      "client_id"     -> Seq(clientId),
      "client_secret" -> Seq(clientSecret),
      "grant_type"    -> Seq("authorization_code"),
      "redirect_uri"  -> Seq(redirectUri)
    )

    "return a token" in {
      val tokenResponseFromAuthorizationCode: JsValue =
        parse(s"""{ "access_token": "$accessToken", "refresh_token": "$refreshToken", "expires_in": $expiresIn }""")
      val response = HttpResponse(200, Some(tokenResponseFromAuthorizationCode))

      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(response))

      val tokenResponse: TokenOauthResponse = service.getTokenFromAccessCode(authCode).futureValue

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
      propagateException(Upstream4xxResponse("4xx", 400, 400))
      propagateException(Upstream5xxResponse("5xx", 500, 500))
      propagateException(new Exception())
    }

    "handle the exception when APIGatewayTokenService returns a code other than 200" in {
      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(HttpResponse(201)))

      intercept[RuntimeException] {
        service.getTokenFromAccessCode(authCode).futureValue
      }
    }

    def handleInvalidResponseJson(tokenResponseFromAuthorizationCode: JsValue) = {
      val response = HttpResponse(200, Some(tokenResponseFromAuthorizationCode))

      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(response))

      intercept[RuntimeException] {
        service.getTokenFromAccessCode(authCode).futureValue
      }
    }

    def propagateException[T <: Exception](exception: T)(implicit ct: ClassTag[T]): Assertion = {
      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.failed(exception))

      val actual = intercept[T] { Await.result(service.getTokenFromAccessCode(authCode), 10 seconds) }
      exception mustBe actual
    }
  }

  "getTokenFromRefreshToken" should {
    val refreshToken = "refresh_token"

    val form = Map(
      refreshToken    -> Seq(refreshToken),
      "client_id"     -> Seq(clientId),
      "client_secret" -> Seq(clientSecret),
      "grant_type"    -> Seq(refreshToken),
      "redirect_uri"  -> Seq(redirectUri)
    )

    "return a token" in {
      val tokenResponseFromAuthorizationCode: JsValue =
        parse(s"""{ "access_token": "$accessToken", "refresh_token": "$refreshToken", "expires_in": $expiresIn }""")
      val response = HttpResponse(200, Some(tokenResponseFromAuthorizationCode))

      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(response))

      val tokenResponse: TokenOauthResponse = service.getTokenFromRefreshToken(refreshToken).futureValue

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
      propagateException(Upstream4xxResponse("4xx", 400, 400))
      propagateException(Upstream5xxResponse("5xx", 500, 500))
      propagateException(new Exception())
    }

    "handle the exception when APIGatewayTokenService returns a code other than 200" in {
      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(HttpResponse(201)))

      intercept[RuntimeException] {
        service.getTokenFromRefreshToken(refreshToken).futureValue
      }
    }

    def handleInvalidResponseJson(tokenResponseFromAuthorizationCode: JsValue) = {
      val response = HttpResponse(200, Some(tokenResponseFromAuthorizationCode))

      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.successful(response))

      intercept[RuntimeException] {
        service.getTokenFromRefreshToken(refreshToken).futureValue
      }
    }

    def propagateException(exception: Exception) = {
      (connector
        .doPostForm(_: String, _: Map[String, Seq[String]])(_: ExecutionContext, _: HeaderCarrier))
        .expects(pathToAPIGatewayTokenService, form, *, *)
        .returning(Future.failed(exception))

      val actual = intercept[Exception] {
        Await.result(service.getTokenFromRefreshToken(refreshToken), 10 seconds)
      }
      exception mustBe actual
    }
  }
}
