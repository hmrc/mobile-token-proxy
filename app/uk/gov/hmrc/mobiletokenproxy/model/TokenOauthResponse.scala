package uk.gov.hmrc.mobiletokenproxy.model

import play.api.libs.json.Json

case class TokenOauthResponse(access_token:String, refresh_token:String, expires_in:Long)

object TokenOauthResponse {
  implicit val format = Json.format[TokenOauthResponse]
}