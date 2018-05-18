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

import java.lang.System.currentTimeMillis

import play.api.libs.json.Json.parse
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.crypto.CryptoWithKeysFromConfig
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.mobiletokenproxy.config.{ApplicationConfig, HttpVerbs}
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.services.{LiveTokenService, TokenService}

import scala.concurrent.{ExecutionContext, Future}

class TokenTestService(connector:GenericConnector, config:ApplicationConfig) extends LiveTokenService {
  override val genericConnector: GenericConnector = connector
  override val appConfig: ApplicationConfig = config
}


trait Setup {
  val timestamp: Long = currentTimeMillis()

  val tokenRequestWithAuthCodeAndRefreshToken: JsValue = parse(s"""{"authorizationCode":"123456789","refreshToken":"some refresh token"}""")
  val tokenRequestWithAuthCode: JsValue = parse(s"""{"authorizationCode":"abcdf"}""")
  val tokenRequestWithRefreshCode: JsValue = parse(s"""{"refreshToken":"abcdf"}""")

  val tokenResponseFromAuthorizationCode: JsValue = parse(
    """{
      |  "access_token": "495b5b1725d590eb87d0f6b7dcea32a9",
      |  "refresh_token": "b75f2ed960898b4cd38f23934c6befb2",
      |  "expires_in": 14400,
      |  "scope": "read:customer-profile read:native-apps-api-orchestration read:personal-income read:submission-tracker read:web-session read:messages write:push-registration",
      |  "token_type": "bearer"
      |}
    """.stripMargin)

  val badTokenResponseFromAuthorizationCode: JsValue = parse(
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

  def addTestHeaders[T](fakeRequest:FakeRequest[T]) = fakeRequest.withHeaders(testHTTPHeaders ++ invalidHTTPHeaders :_*)

  def buildMessage(code:String, message:String) = s"""{"code":"$code","message":"$message"}"""

  // NGC-3236 review
  val config = new ApplicationConfig {
    override lazy val analyticsHost: String = "somehost"
    override lazy val analyticsToken: Some[String] = Some("123")
    override lazy val pathToAPIGatewayTokenService: String = "http://localhost:8236/oauth/token"
    override lazy val client_id: String = "client_id"
    override lazy val redirect_uri: String = "redirect_uri"
    override lazy val client_secret: String = "client_secret"
    override lazy val pathToAPIGatewayAuthService: String = "http://localhost:8236/oauth/authorize"
    override lazy val scope: String = "some-scopes"
    override lazy val response_type: String = "code"
    override lazy val tax_calc_token: String = "tax_calc_server_token"
    // Note case is different in order to verify case is ignored.
    override lazy val passthroughHttpHeaders: Seq[String] = Seq("X-Vendor-Instance-id", "X-Client-Device-id")
    override val expiryDecrement: Option[Long] = None
  }

  def fakeRequest(body:JsValue) = FakeRequest(POST, "url").withBody(body)
    .withHeaders("Content-Type" -> "application/json")

  lazy val jsonRequestEmpty: FakeRequest[JsValue] = fakeRequest(parse("{}"))
  lazy val jsonRequestWithAuthCodeAndRefreshToken: FakeRequest[JsValue] = fakeRequest(tokenRequestWithAuthCodeAndRefreshToken)
  lazy val jsonRequestWithAuthCode: FakeRequest[JsValue] = fakeRequest(tokenRequestWithAuthCode)
  lazy val jsonRequestWithRefreshCode: FakeRequest[JsValue] = fakeRequest(tokenRequestWithRefreshCode)


  val emptyRequest = FakeRequest()

  class Response(jsonIn:JsValue, responseStatus:Int) extends HttpResponse {
    override def body: String = Json.stringify(jsonIn)
    override def json: JsValue = jsonIn
    override def allHeaders: Map[String, Seq[String]] = Map.empty
    override def status: Int = responseStatus
  }

  // NGC-3236 review
  val http: HttpVerbs = new HttpVerbs {
    override val hooks: Seq[HttpHook] = NoneRequired
    private val response: Future[HttpResponse] = Future successful HttpResponse(200)

    override def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = response

    override def doPost[A](url: String, body: A, headerApiGatewayProxyISpecs: Seq[(String, String)])(implicit wts: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = response

    override def doPostString(url: String, body: String, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[HttpResponse] = response

    override def doFormPost(url: String, body: Map[String, Seq[String]])(implicit hc: HeaderCarrier): Future[HttpResponse] = response

    override def doEmptyPost[A](url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = response
  }

  class GenericTestConnector(response:Option[JsValue], exception:Option[Exception])
    extends GenericConnector(http) {
    var headers:Seq[(String, String)] = Seq.empty
    var path = ""

    def buildResponse(inPath:String)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[Response] = {
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
  }

}

trait SuccessAccessCode extends Setup {
  def connector() = new GenericTestConnector(Some(tokenResponseFromAuthorizationCode), None)

  val controller = new MobileTokenProxy(connector(), config,  new TokenTestService(connector(), config): TokenService) {
    override implicit val ec: ExecutionContext = ExecutionContext.global

    override val aes: CryptoWithKeysFromConfig = CryptoWithKeysFromConfig("aes")
  }
}

trait FailToReturnApiToken extends Setup {

  val ex:Option[Exception]
  val connector = new GenericTestConnector(Some(tokenResponseFromAuthorizationCode), ex)

  val controller = new MobileTokenProxy(connector, config,  new TokenTestService(connector, config): TokenService) {
    override implicit val ec: ExecutionContext = ExecutionContext.global

    override val aes: CryptoWithKeysFromConfig = CryptoWithKeysFromConfig("aes")
  }
}

trait BadResponseAPIGateway extends Setup {
  val connector = new GenericTestConnector(Some(badTokenResponseFromAuthorizationCode), None)

  val controller = new MobileTokenProxy(connector, config,  new TokenTestService(connector, config): TokenService) {
    override implicit val ec: ExecutionContext = ExecutionContext.global

    override val aes: CryptoWithKeysFromConfig = CryptoWithKeysFromConfig("aes")
  }
}


trait SuccessRefreshCode extends Setup {

  val tokenRequestWithRefreshToken: JsValue = parse(s"""{"refreshToken":"some_refresh_token"}""")
  val jsonRequestRequestWithRefreshToken: FakeRequest[JsValue] = fakeRequest(tokenRequestWithRefreshToken)
  val connector = new GenericTestConnector(Some(tokenResponseFromAuthorizationCode), None)

  val controller = new MobileTokenProxy(connector, config,  new TokenTestService(connector, config): TokenService) {
    override implicit val ec: ExecutionContext = ExecutionContext.global

    override val aes: CryptoWithKeysFromConfig = CryptoWithKeysFromConfig("aes")
  }
}

class SuccessExpiryDecrement(expiryDecrementConfig: Long) extends Setup {

  val tokenRequestWithRefreshToken: JsValue = parse(s"""{"refreshToken":"some_refresh_token"}""")
  val jsonRequestRequestWithRefreshToken: FakeRequest[JsValue] = fakeRequest(tokenRequestWithRefreshToken)

  // NGC-3236 review
  override val config = new ApplicationConfig {
    override lazy val analyticsHost: String = "somehost"
    override lazy val analyticsToken: Some[String] = Some("123")
    override lazy val pathToAPIGatewayTokenService: String = "http://localhost:8236/oauth/token"
    override lazy val client_id: String = "client_id"
    override lazy val redirect_uri: String = "redirect_uri"
    override lazy val client_secret: String = "client_secret"
    override lazy val pathToAPIGatewayAuthService: String = "http://localhost:8236/oauth/authorize"
    override lazy val scope: String = "some-scopes"
    override lazy val response_type: String = "code"
    override lazy val tax_calc_token: String = "tax_calc_server_token"
    // Note case is different in order to verify case is ignored.
    override lazy val passthroughHttpHeaders: Seq[String] = Seq("X-Vendor-Instance-id", "X-Client-Device-id")
    override val expiryDecrement: Option[Long] = Option(expiryDecrementConfig)
  }

  val connector = new GenericTestConnector(Some(tokenResponseFromAuthorizationCode), None)

  val controller = new MobileTokenProxy(connector, config, new TokenTestService(connector, config)) {
    override implicit val ec: ExecutionContext = ExecutionContext.global

    override val aes: CryptoWithKeysFromConfig = CryptoWithKeysFromConfig("aes")
  }
}
