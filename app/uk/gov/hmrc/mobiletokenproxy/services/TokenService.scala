package uk.gov.hmrc.mobiletokenproxy.services

import java.net.URLEncoder

import play.api.Logger
import play.api.Play._
import play.api.libs.json._
import uk.gov.hmrc.mobiletokenproxy.config.ApplicationConfig
import uk.gov.hmrc.mobiletokenproxy.connectors.GenericConnector
import uk.gov.hmrc.mobiletokenproxy.model.{UEID, FoundToken, TokenOauthResponse}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


class InvalidAccessCode extends Exception

class FailToRetrieveToken extends Exception

class FailToSaveToken(token:TokenOauthResponse) extends Exception {
  def getToken = token
}

object FailToSaveToken {
  def unapply(ex: FailToSaveToken) = Some(ex.getToken)
}

case class UpdateRefreshToken(deviceId:String, refreshToken: String)

object UpdateRefreshToken {
  implicit val format = Json.format[UpdateRefreshToken]
}

case class RefreshFromToken(recordId:String, timestamp:Long, authToken:TokenOauthResponse)

trait TokenService {
  def getTokenFromAccessCode(authCode: String, deviceId:String, timestamp: Long)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[(String, TokenOauthResponse)]

  def getTokenFromRefreshToken(deviceId: String, ueid: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[RefreshFromToken]]

  def getAPIGatewayToken(key: String, code: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[TokenOauthResponse]

  def saveToken(deviceId:String, tokenOauthResponse: TokenOauthResponse, time: Long)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[String]

  def findToken(deviceId: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[FoundToken]]

  def updateRefreshToken(deviceId: String, refreshToken:String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[FoundToken]
}

trait LiveTokenService extends TokenService {
  def genericConnector: GenericConnector
  def appConfig: ApplicationConfig

  def getConfig(key:String) = current.configuration.getString(key).getOrElse(throw new IllegalArgumentException(s"Failed to resolve $key"))

  def getTokenFromAccessCode(authCode:String, deviceId:String, timestamp:Long)(implicit hc: HeaderCarrier, ex:ExecutionContext): Future[(String, TokenOauthResponse)] = {
    for {
      tokenOauth <- getAPIGatewayToken("code",authCode)
      saveToken <- saveToken(deviceId, tokenOauth, timestamp)
    } yield(saveToken, tokenOauth)
  }

  def getTokenFromRefreshToken(deviceId:String, ueid:String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[RefreshFromToken]] = {

    findToken(deviceId).flatMap {

      case Some(foundToken:FoundToken) =>
// TODO: Add tests for previous!
        // Check the UEID supplied matches the associated saved record hash.
        if (UEID.deviceIdHashIsValid(ueid, foundToken.deviceId, foundToken.recordId, foundToken.timestamp, appConfig.secret, appConfig.previousSecrets)) {
          // Request for new access-token using refresh token from API Gateway.
          getAPIGatewayToken("refresh_token", foundToken.refreshToken).flatMap(response =>
            updateRefreshToken(deviceId, response.refresh_token).flatMap(updatedToken =>
              Future.successful(Some(RefreshFromToken(updatedToken.recordId, updatedToken.timestamp, response)))
            ).recover {
// TODO: Add metrics
              case e:Exception => throw new FailToSaveToken(response)
            }
          )
        } else {
          Logger.error("The UEID could not be matched against associated record!")
          Future.successful(None)
        }

      case None => Future.successful(None)
    }
  }

  def getAPIGatewayToken(key:String, code: String)(implicit hc: HeaderCarrier, ex:ExecutionContext): Future[TokenOauthResponse] = {

    val form = Map(
      key -> Seq(code),
      "client_id" -> Seq(appConfig.client_id),
      "client_secret" -> Seq(appConfig.client_secret),
      "grant_type" -> Seq(appConfig.grant_type),
      "redirect_uri" -> Seq(appConfig.redirect_uri)
    )

    genericConnector.doPostForm(appConfig.pathToAPIGateway, form).map(result => {
      result.status match {
        case 200 =>

          val accessToken = (result.json \ "access_token").asOpt[String]
          val refreshToken = (result.json \ "refresh_token").asOpt[String]
          val expiresIn = (result.json \ "expires_in").asOpt[Long]

          if (accessToken.isDefined&&refreshToken.isDefined&&expiresIn.isDefined) {
            TokenOauthResponse(accessToken.get, refreshToken.get, expiresIn.get)
          } else {
// TODO: Add metrics
            throw new IllegalArgumentException(s"Failed to read the JSON result attributes from ${result.json}.")
          }

        case _ => throw new FailToRetrieveToken
      }
    }).recover {
      case ex:uk.gov.hmrc.play.http.BadRequestException => throw new InvalidAccessCode
    }
  }

  def saveToken(deviceId:String, tokenOauthResponse:TokenOauthResponse, time:Long)(implicit hc: HeaderCarrier, ex:ExecutionContext): Future[String] = {
    val json=Json.parse(s"""{"deviceId":"${deviceId}", "refreshToken":"${tokenOauthResponse.refresh_token}", "timestamp":$time}""")

    genericConnector.doPost(appConfig.pathToTokenExchange, json).map(resp =>
      resp.status match {
        case 200 | 201 => (resp.json \ "id").as[String]

        case _ =>
// TODO: Add metrics
          Logger.error(s"None 200 response returned from token exchange service. Status is ${resp.status}.")
          throw new FailToSaveToken(tokenOauthResponse)
      }).recover {
      case ex:Exception =>
// TODO: Add metrics
        Logger.error(s"Failed to communicate to token exchange service. Exception is ${ex.printStackTrace()}")
        throw new FailToSaveToken(tokenOauthResponse)
    }
  }

  def findToken(deviceId:String)(implicit hc: HeaderCarrier, ex:ExecutionContext): Future[Option[FoundToken]] = {

    genericConnector.doGet(s"${appConfig.pathToTokenExchange}/${URLEncoder.encode(deviceId, "UTF-8")}").map { resp =>
      resp.status match {
        case 200 => Some((resp.json).as[FoundToken])
      }
    }.recover {
      case ex:uk.gov.hmrc.play.http.NotFoundException => None
    }
  }

  def updateRefreshToken(deviceId:String, refreshToken:String)(implicit hc: HeaderCarrier, ex:ExecutionContext): Future[FoundToken] = {
    val update = UpdateRefreshToken(deviceId, refreshToken)

    genericConnector.doPostRefresh(appConfig.pathToTokenUpdate, Json.toJson(update)).map { resp =>
      resp.status match {
        case 200 => (resp.json).as[FoundToken]
      }
    }
  }
}


object LiveTokenService extends LiveTokenService {
  override def genericConnector: GenericConnector = GenericConnector
  override def appConfig = ApplicationConfig
}



