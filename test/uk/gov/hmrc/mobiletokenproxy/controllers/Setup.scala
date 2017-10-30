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

import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.crypto.CryptoWithKeysFromConfig
import uk.gov.hmrc.http.{CoreGet, CorePost, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.mobiletokenproxy.config.ApplicationConfig
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.services.{LiveTokenService, TokenService}

import scala.concurrent.{ExecutionContext, Future}

class TokenTestService(connector:GenericConnector, config:ApplicationConfig) extends LiveTokenService {
  override def genericConnector: GenericConnector = connector
  override def appConfig: ApplicationConfig = config
}


trait Setup {
  val timestamp = System.currentTimeMillis()

  val tokenRequestWithAuthCodeAndRefreshToken = Json.parse(s"""{"authorizationCode":"123456789","refreshToken":"some refresh token"}""")
  val tokenRequestWithAuthCode = Json.parse(s"""{"authorizationCode":"abcdf"}""")
  val tokenRequestWithRefreshCode = Json.parse(s"""{"refreshToken":"abcdf"}""")

  val tokenResponseFromAuthorizationCode = Json.parse(
    """{
      |  "access_token": "495b5b1725d590eb87d0f6b7dcea32a9",
      |  "refresh_token": "b75f2ed960898b4cd38f23934c6befb2",
      |  "expires_in": 14400,
      |  "scope": "read:customer-profile read:native-apps-api-orchestration read:personal-income read:submission-tracker read:web-session read:messages write:push-registration",
      |  "token_type": "bearer"
      |}
    """.stripMargin)

  val badTokenResponseFromAuthorizationCode = Json.parse(
    """{
      |  "refresh_token": "b75f2ed960898b4cd38f23934c6befb2",
      |  "expires_in": 14400,
      |  "scope": "read:customer-profile read:native-apps-api-orchestration read:personal-income read:submission-tracker read:web-session read:messages write:push-registration",
      |  "token_type": "bearer"
      |}
    """.stripMargin)


  val vendorHeader = "X-Vendor-Instance-Id"
  val deviceIdHeader = "X-Client-Device-ID"

  val testHTTPHeaders = Seq(vendorHeader -> "header vendor", deviceIdHeader -> "header device Id")
  val invalidHTTPHeaders = Seq("testa" -> "valuea", "testb" -> "valueb")

  def addTestHeaders[T](fakeRequest:FakeRequest[T]) = fakeRequest.withHeaders((testHTTPHeaders ++ invalidHTTPHeaders) :_*)

  def buildMessage(code:String, message:String) = s"""{"code":"$code","message":"$message"}"""

  val config = new ApplicationConfig {
    override val analyticsHost: String = "somehost"
    override val analyticsToken: Option[String] = None
    override val pathToAPIGatewayTokenService: String = "http://localhost:8236/oauth/token"
    override val client_id: String = "client_id"
    override val redirect_uri: String = "redirect_uri"
    override val client_secret: String = "client_secret"
    override val pathToAPIGatewayAuthService: String = "http://localhost:8236/oauth/authorize"
    override val scope: String = "some-scopes"
    override val response_type: String = "code"
    override val tax_calc_token: String = "tax_calc_server_token"
    // Note case is different in order to verify case is ignored.
    override val passthroughHttpHeaders: Seq[String] = Seq("X-Vendor-Instance-id", "X-Client-Device-id")
    override val expiryDecrement: Option[Long] = None
  }

  def fakeRequest(body:JsValue) = FakeRequest(POST, "url").withBody(body)
    .withHeaders("Content-Type" -> "application/json")

  lazy val jsonRequestEmpty = fakeRequest(Json.parse("{}"))
  lazy val jsonRequestWithAuthCodeAndRefreshToken = fakeRequest(tokenRequestWithAuthCodeAndRefreshToken)
  lazy val jsonRequestWithAuthCode = fakeRequest(tokenRequestWithAuthCode)
  lazy val jsonRequestWithRefreshCode = fakeRequest(tokenRequestWithRefreshCode)


  val emptyRequest = FakeRequest()

  class Response(jsonIn:JsValue, responseStatus:Int) extends HttpResponse {
    override def body = Json.stringify(jsonIn)
    override def json = jsonIn
    override def allHeaders: Map[String, Seq[String]] = Map.empty
    override def status = responseStatus
  }

  class GenericTestConnector(response:Option[JsValue], exception:Option[Exception])
    extends GenericConnector {
    var headers:Seq[(String, String)] = Seq.empty
    var path = ""

    def buildResponse(inPath:String)(implicit ec : ExecutionContext, hc : HeaderCarrier) = {
      path = inPath
      headers = hc.headers
      exception.fold(Future.successful(new Response(response.get, 200))) { ex => Future.failed(ex) }
    }

    override def doGet(path:String)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
      buildResponse(path)
    }

    override def doPost(path:String, json:JsValue)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
      buildResponse(path)
    }

    override def doPostForm(path: String, form: Map[String, Seq[String]])(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
      buildResponse(path)
    }

    override def http: CorePost with CoreGet = throw new Exception("Not implemented!")
  }

}

trait SuccessAccessCode extends Setup {

  val controller = new MobileTokenProxy {

    lazy val connector = new GenericTestConnector(Some(tokenResponseFromAuthorizationCode), None)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global

    override val aes: CryptoWithKeysFromConfig = CryptoWithKeysFromConfig("aes")
  }
}

trait FailToReturnApiToken extends Setup {

  val ex:Option[Exception]

  val controller = new MobileTokenProxy {

    lazy val connector = new GenericTestConnector(Some(tokenResponseFromAuthorizationCode), ex)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global

    override val aes: CryptoWithKeysFromConfig = CryptoWithKeysFromConfig("aes")
  }

}

trait BadResponseAPIGateway extends Setup {

  val controller = new MobileTokenProxy {

    lazy val connector = new GenericTestConnector(Some(badTokenResponseFromAuthorizationCode), None)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global

    override val aes: CryptoWithKeysFromConfig = CryptoWithKeysFromConfig("aes")
  }
}


trait SuccessRefreshCode extends Setup {

  val tokenRequestWithRefreshToken = Json.parse(s"""{"refreshToken":"some_refresh_token"}""")
  val jsonRequestRequestWithRefreshToken = fakeRequest(tokenRequestWithRefreshToken)

  val controller = new MobileTokenProxy {

    lazy val connector = new GenericTestConnector(Some(tokenResponseFromAuthorizationCode), None)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global

    override val aes: CryptoWithKeysFromConfig = CryptoWithKeysFromConfig("aes")
  }
}

class SuccessExpiryDecrement(expiryDecrementConfig: Long) extends Setup {

  val tokenRequestWithRefreshToken = Json.parse(s"""{"refreshToken":"some_refresh_token"}""")
  val jsonRequestRequestWithRefreshToken = fakeRequest(tokenRequestWithRefreshToken)

  override val config = new ApplicationConfig {
    override val analyticsHost: String = "somehost"
    override val analyticsToken: Option[String] = None
    override val pathToAPIGatewayTokenService: String = "http://localhost:8236/oauth/token"
    override val client_id: String = "client_id"
    override val redirect_uri: String = "redirect_uri"
    override val client_secret: String = "client_secret"
    override val pathToAPIGatewayAuthService: String = "http://localhost:8236/oauth/authorize"
    override val scope: String = "some-scopes"
    override val response_type: String = "code"
    override val tax_calc_token: String = "tax_calc_server_token"
    // Note case is different in order to verify case is ignored.
    override val passthroughHttpHeaders: Seq[String] = Seq("X-Vendor-Instance-id", "X-Client-Device-id")
    override val expiryDecrement: Option[Long] = Option(expiryDecrementConfig)
  }

  val controller = new MobileTokenProxy {

    lazy val connector = new GenericTestConnector(Some(tokenResponseFromAuthorizationCode), None)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global

    override val aes: CryptoWithKeysFromConfig = CryptoWithKeysFromConfig("aes")
  }
}
