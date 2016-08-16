package uk.gov.hmrc.mobiletokenproxy.connectors

import play.api.libs.json._
import uk.gov.hmrc.mobiletokenproxy.config.StubWsHttp
import uk.gov.hmrc.play.http.{HttpResponse, HttpGet, HeaderCarrier, HttpPost}

import scala.concurrent.{ExecutionContext, Future}

trait GenericConnector {

  def http: HttpPost with HttpGet

  def doGet(path:String)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
    http.GET(path)
  }

  def doPostRefresh(path:String, json:JsValue)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
    http.POST(path, json)
  }

  def doPost(path:String, json:JsValue)(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
    http.POST(path, json)
  }

  def doPostForm(path:String, form:Map[String,Seq[String]])(implicit ec : ExecutionContext, hc : HeaderCarrier): Future[HttpResponse] = {
    http.POSTForm(path, form)
  }
}

object GenericConnector extends GenericConnector {
  override def http = StubWsHttp
}

