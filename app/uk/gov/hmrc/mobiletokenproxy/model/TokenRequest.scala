package uk.gov.hmrc.mobiletokenproxy.model

import play.api.libs.json.Json

case class TokenRequest(deviceId:String, authorizationCode:Option[String], ueid:Option[String])

object TokenRequest {
  implicit val format = Json.format[TokenRequest]
}