package uk.gov.hmrc.mobiletokenproxy.model

import play.api.libs.json.Json


case class TokenResponse(accessToken:String, ueid:Option[String], expireTime:Long)

object TokenResponse {
  implicit val format = Json.format[TokenResponse]
}