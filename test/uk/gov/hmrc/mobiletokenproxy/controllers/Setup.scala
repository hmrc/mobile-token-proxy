package uk.gov.hmrc.mobiletokenproxy.controllers

import play.api.libs.json.{Json, JsValue}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.mobiletokenproxy.config.ApplicationConfig
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.model.UEID
import uk.gov.hmrc.mobiletokenproxy.services.{LiveTokenService, TokenService}
import uk.gov.hmrc.play.http._

import scala.concurrent.{ExecutionContext, Future}

class TokenTestService(connector:GenericConnector, config:ApplicationConfig) extends LiveTokenService {
  override def genericConnector: GenericConnector = connector
  override def appConfig: ApplicationConfig = config
}


trait Setup {

  val deviceId = "deviceId1234"
  val recordId = "abcdefg"
  val id = "some-login-id"
  val timestamp = System.currentTimeMillis()

  val tokenRequestWithDeviceId = Json.parse(s"""{"deviceId":"$deviceId"}""")
  val tokenRequestWithAuthCodeAndUeid = Json.parse(s"""{"deviceId":"$deviceId","authorizationCode":"abcdf","ueid":"ghijk"}""")
  val tokenRequestWithAuthCode = Json.parse(s"""{"deviceId":"$deviceId","authorizationCode":"abcdf"}""")

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


  val idResponse = Json.parse(s"""{"id":"$recordId"}""")

  val config = new ApplicationConfig {
    override val assetsPrefix: String = "someprefix"
    override val analyticsHost: String = "somehost"
    override val analyticsToken: Option[String] = None
    override val pathToTokenExchange: String = "someexchangeurl"
    override val pathToAPIGateway: String = "someapigatewayurl"
    override val pathToTokenUpdate: String = "someupdateurl"
    override val client_id: String = "client_id"
    override val redirect_uri: String = "redirect_uri"
    override val client_secret: String = "client_secret"
    override val grant_type: String = "grant_type"
    override val secret: String = "some_secret"
    override val previousSecrets: Seq[String] = Seq.empty
  }

  def fakeRequest(body:JsValue) = FakeRequest(POST, "url").withBody(body)
    .withHeaders("Content-Type" -> "application/json")

  lazy val jsonRequestEmpty = fakeRequest(Json.parse("{}"))
  lazy val jsonRequestWithDeviceId = fakeRequest(tokenRequestWithDeviceId)
  lazy val jsonRequestRequestWithAuthCodeAndUeid = fakeRequest(tokenRequestWithAuthCodeAndUeid)
  lazy val jsonRequestRequestWithAuth = fakeRequest(tokenRequestWithAuthCode)


  val emptyRequest = FakeRequest()

  val defaultTokenMap = Map(
    "client_id"  -> "client_id",
    "client_secret"  -> "client_secret",
    "grant_type"  -> "grant_type",
    "redirect_uri"  -> "redirect_uri"
  )

  def token_request(code:String) = Map("code"  -> code) ++ defaultTokenMap
  def refresh_token_request(code:String) = Map("refresh_token"  -> code) ++ defaultTokenMap
  def fakeRequest(body:JsValue, url:String) = FakeRequest(POST, url).withBody(body).withHeaders("Content-Type" -> "application/json", "Authorization"->"Bearer some-login-id", "test-header"->"test-value")
  def fakeRequestNoBearerToken(body:JsValue, url:String) = FakeRequest(POST, url).withBody(body).withHeaders("Content-Type" -> "application/json")

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

  class GenericTestConnector(get:Option[JsValue], post:Option[JsValue], postForm:Option[JsValue], postRefresh:Option[JsValue],
                             status:Int, exceptionDoPostForm:Boolean, exceptionDoPost:Boolean, exceptionGet:Int, exceptionRefresh:Boolean)
    extends GenericConnector with Recorder {

    def withRecord(path:String)(func:Future[HttpResponse]) = {
      recordPath = Some(path)
      func
    }

    override def doGet(path:String)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
      if (exceptionGet==1) Future.failed(new NotFoundException("Controlled explosion!"))
      else if (exceptionGet==2) Future.failed(new BadRequestException("Controlled explosion!"))
      else {
        withRecord(path) {
          Future.successful(new Response(get.get, status))
        }
      }
    }

    override def doPost(path:String, json:JsValue)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {

      if (exceptionDoPost) Future.failed(new BadRequestException("Controlled explosion!"))
      else {
        withRecord(path) {
          Future.successful(new Response(post.get, status))
        }
      }
    }

    override def doPostRefresh(path:String, json:JsValue)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
      if (exceptionRefresh) Future.failed(new BadRequestException("Controlled explosion!"))
      else {
        withRecord(path) {
          Future.successful(new Response(postRefresh.get, status))
        }
      }
    }

    override def doPostForm(path:String, form:Map[String,Seq[String]])(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
      if (exceptionDoPostForm) Future.failed(new BadRequestException("Controlled explosion!"))
      else {
        withRecord(path) {
          Future.successful(new Response(postForm.get, status))
        }
      }
    }

    override def http: HttpPost with HttpGet = ???
  }

}

trait SuccessAccessCode extends Setup {

  def buildUEID(deviceId: String, recordId: String) = UEID.generateHash(deviceId, recordId, timestamp, config.secret)

  val controller = new MobileTokenProxy {

    override def buildTimestamp = timestamp

    lazy val connector = new GenericTestConnector(None, Some(idResponse), Some(tokenResponseFromAuthorizationCode), None,  200, false, false, 0, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}

trait FailToReturnApiToken extends Setup {
  def buildUEID(deviceId: String, recordId: String) = UEID.generateHash(deviceId, recordId, timestamp, config.secret)

  val controller = new MobileTokenProxy {

    override def buildTimestamp = timestamp

    lazy val connector = new GenericTestConnector(None, Some(idResponse), Some(tokenResponseFromAuthorizationCode), None, 500, false, false, 0, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }

}

trait BadRequestExceptionReturnApiToken extends Setup {

  def buildUEID(deviceId: String, recordId: String) = UEID.generateHash(deviceId, recordId, timestamp, config.secret)

  val controller = new MobileTokenProxy {

    override def buildTimestamp = timestamp

    lazy val connector = new GenericTestConnector(None, Some(idResponse), Some(tokenResponseFromAuthorizationCode), None, 500, true, false, 0, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}

trait BadResponseAPIGateway extends Setup {

  def buildUEID(deviceId: String, recordId: String) = UEID.generateHash(deviceId, recordId, timestamp, config.secret)

  val controller = new MobileTokenProxy {

    override def buildTimestamp = timestamp

    lazy val connector = new GenericTestConnector(None, Some(idResponse), Some(badTokenResponseFromAuthorizationCode), None, 200, false, false, 0, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}

trait FailToSaveToken extends Setup {

  def buildUEID(deviceId: String, recordId: String) = UEID.generateHash(deviceId, recordId, timestamp, config.secret)

  val controller = new MobileTokenProxy {

    override def buildTimestamp = timestamp

    lazy val connector = new GenericTestConnector(None, Some(idResponse), Some(tokenResponseFromAuthorizationCode), None, 200, false, true, 0, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}

trait Refresh extends Setup {

  def buildUEID(deviceId: String, recordId: String) = UEID.generateHash(deviceId, recordId, timestamp, config.secret)

  val ueid = buildUEID(deviceId, recordId)

  val tokenRequestWithUeidToken = Json.parse(s"""{"deviceId":"$deviceId","ueid":"$ueid"}""")
  val jsonRequestRequestWithUeid = fakeRequest(tokenRequestWithUeidToken)

  val refresh_token = "some_refresh_token"
  val foundTokenResponse = Json.parse(s"""{"recordId":"$recordId", "deviceId":"$deviceId", "refreshToken":"$refresh_token", "timestamp":$timestamp}""")

}

trait SuccessRefreshCode extends Refresh {

  val controller = new MobileTokenProxy {

    override def buildTimestamp = timestamp

    lazy val connector = new GenericTestConnector(Some(foundTokenResponse), Some(idResponse), Some(tokenResponseFromAuthorizationCode), Some(foundTokenResponse), 200, false, false, 0, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}

trait NotFoundDeviceIdRefresh extends Refresh {

  val controller = new MobileTokenProxy {

    override def buildTimestamp = timestamp

    lazy val connector = new GenericTestConnector(None, None, Some(tokenResponseFromAuthorizationCode), Some(foundTokenResponse), 200, false, false, 1, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}

trait FailToFindTokenForRefresh extends Refresh {

  val controller = new MobileTokenProxy {

    override def buildTimestamp = timestamp

    lazy val connector = new GenericTestConnector(None, None, Some(tokenResponseFromAuthorizationCode), Some(foundTokenResponse), 200, false, false, 2, false)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}

trait FailToSaveTokenForRefresh extends Refresh {

  val controller = new MobileTokenProxy {

    override def buildTimestamp = timestamp

    lazy val connector = new GenericTestConnector(Some(foundTokenResponse), None, Some(tokenResponseFromAuthorizationCode), Some(foundTokenResponse), 200, false, false, 0, true)

    override def genericConnector: GenericConnector = connector

    override def appConfig: ApplicationConfig = config

    override val service: TokenService = new TokenTestService(genericConnector, appConfig)

    override implicit val ec: ExecutionContext = ExecutionContext.global
  }
}

