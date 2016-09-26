/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.libs.json.{Json, JsValue}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.mobiletokenproxy.config.ApplicationConfig
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.services.{LiveTokenService, TokenService}
import uk.gov.hmrc.play.http._

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

  val config = new ApplicationConfig {
    override val assetsPrefix: String = "someprefix"
    override val analyticsHost: String = "somehost"
    override val analyticsToken: Option[String] = None
    override val pathToAPIGatewayTokenService: String = "http://localhost:8236/oauth/token"
    override val client_id: String = "client_id"
    override val redirect_uri: String = "redirect_uri"
    override val client_secret: String = "client_secret"
    override val grant_type: String = "grant_type"
    override val pathToAPIGatewayAuthService: String = "http://localhost:8236/oauth/authorize"
    override val scope: String = "some-scopes"
    override val response_type: String = "code"
    override val tax_calc_token: String = "some-tax-calc-service-token"
  }

  def fakeRequest(body:JsValue) = FakeRequest(POST, "url").withBody(body)
    .withHeaders("Content-Type" -> "application/json")

  lazy val jsonRequestEmpty = fakeRequest(Json.parse("{}"))
  lazy val jsonRequestWithAuthCodeAndRefreshToken = fakeRequest(tokenRequestWithAuthCodeAndRefreshToken)
  lazy val jsonRequestRequestWithAuthCode = fakeRequest(tokenRequestWithAuthCode)
  lazy val jsonRequestRequestWithRefreshCode = fakeRequest(tokenRequestWithRefreshCode)


  val emptyRequest = FakeRequest()

  val defaultTokenMap = Map(
    "client_id"  -> "client_id",
    "client_secret"  -> "client_secret",
    "grant_type"  -> "grant_type",
    "redirect_uri"  -> "redirect_uri"
  )

  trait Recorder {
    var recordPath : Option[String] = None
    var recordHeaders : Seq[(String, String)] = Seq.empty
  }

  class Response(jsonIn:JsValue, responseStatus:Int) extends HttpResponse {
    override def body = Json.stringify(jsonIn)
    override def json = jsonIn
    override def allHeaders: Map[String, Seq[String]] = Map.empty
    override def status = responseStatus
  }

  class GenericTestConnector(get:Option[JsValue], post:Option[JsValue], postForm:Option[JsValue],
                             status:Int) //, exceptionDoPostForm:Boolean, exceptionDoPost:Boolean, exceptionGet:Int, exceptionRefresh:Boolean)
    extends GenericConnector with Recorder {

    def withRecord(path:String)(func:Future[HttpResponse]) = {
      recordPath = Some(path)
      func
    }

    override def doGet(path:String)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
      withRecord(path) {
        Future.successful(new Response(get.get, status))
      }
    }

    override def doPost(path:String, json:JsValue)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
      withRecord(path) {
        Future.successful(new Response(post.get, status))
      }
    }

    override def doPostForm(path:String, form:Map[String,Seq[String]])(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
      withRecord(path) {
        Future.successful(new Response(postForm.get, status))
      }
    }

    override def http: HttpPost with HttpGet = throw new Exception("Not implemented!")
  }

}

trait SuccessAccessCode extends Setup {

  val controller = new MobileTokenProxy {

    lazy val connector = new GenericTestConnector(None, None, Some(tokenResponseFromAuthorizationCode), 200)//, false, false, 0, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}

trait FailToReturnApiToken extends Setup {

  val httpCode:Int

  val controller = new MobileTokenProxy {

    lazy val connector = new GenericTestConnector(None, None, Some(tokenResponseFromAuthorizationCode), httpCode)//, false, false, 0, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }

}

trait BadResponseAPIGateway extends Setup {

  val controller = new MobileTokenProxy {

    lazy val connector = new GenericTestConnector(None, None, Some(badTokenResponseFromAuthorizationCode), 200) //, false, false, 0, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}


trait SuccessRefreshCode extends Setup {

  val tokenRequestWithRefreshToken = Json.parse(s"""{"refreshToken":"some_refresh_token"}""")
  val jsonRequestRequestWithRefreshToken = fakeRequest(tokenRequestWithRefreshToken)


  val controller = new MobileTokenProxy {

    lazy val connector = new GenericTestConnector(None, None, Some(tokenResponseFromAuthorizationCode), 200) //, false, false, 0, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}
