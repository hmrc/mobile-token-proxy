package uk.gov.hmrc.mobiletokenproxy.config

import play.api.{Logger, Play}
import play.api.Play._
import uk.gov.hmrc.play.config.ServicesConfig

trait ApplicationConfig {
    val assetsPrefix: String
    val analyticsToken: Option[String]
    val analyticsHost: String
    val pathToTokenExchange:String
    val pathToAPIGateway:String
    val pathToTokenUpdate:String
    val client_id:String
    val client_secret:String
    val grant_type:String
    val redirect_uri:String
    val secret:String
    val previousSecrets:Seq[String]
}

object ApplicationConfig extends ApplicationConfig with ServicesConfig {
  private def loadConfig(key: String) = configuration.getString(key).getOrElse(throw new RuntimeException(s"Missing key: $key"))
  override lazy val assetsPrefix: String = loadConfig("assets.url") + loadConfig("assets.version")
  override lazy val analyticsToken =  Some(loadConfig("google-analytics.token"))
  override lazy val analyticsHost: String = loadConfig("google-analytics.host")
  override lazy val pathToTokenExchange = loadConfig("api-gateway.pathToTokenExchange")
  override lazy val pathToTokenUpdate = loadConfig("api-gateway.pathToTokenUpdate")
  override lazy val pathToAPIGateway = loadConfig("api-gateway.pathToAPIGateway")
  override val client_id: String = loadConfig("api-gateway.client_id")
  override val redirect_uri: String = loadConfig("api-gateway.redirect_uri")
  override val client_secret: String = loadConfig("api-gateway.client_secret")
  override val grant_type: String = loadConfig("api-gateway.grant_type")

  final val currentSecret = "ueid.secret"
  final val previousSecret = "ueid.previous.secret"
  final val message = "Missing required configuration entry for mobile-token-proxy: "

  override lazy val secret: String = Play.configuration.getString(currentSecret).getOrElse {
    Logger.error(s"$message $currentSecret")
    throw new SecurityException(s"$message $currentSecret")
  }

  override lazy val previousSecrets: Seq[String] = {
    Play.current.configuration.getStringSeq(previousSecret).getOrElse(Seq.empty)
  }

}
