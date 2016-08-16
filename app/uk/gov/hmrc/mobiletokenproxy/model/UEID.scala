package uk.gov.hmrc.mobiletokenproxy.model

import java.security.MessageDigest
import org.apache.commons.codec.binary.Base64

object UEID {
  final val Token = "#"

  def generateHash(deviceId:String, recordId:String, timestamp:Long, secret:String) = {
    val oneWayHash = s"$deviceId$Token$recordId$Token$timestamp"
    val digest = MessageDigest.getInstance("MD5").digest((oneWayHash + secret).getBytes)
    new String(Base64.encodeBase64(digest))
  }

  def deviceIdHashIsValid(hash:String, deviceId:String, recordId:String, timestamp:Long, secretKey:String, previousSecretKeys:Seq[String]) = {
    val secrets = Seq(secretKey) ++ previousSecretKeys
    val hashChecker = secrets.map(secret => () => hash == generateHash(deviceId, recordId, timestamp, secret)).toStream
    hashChecker.map(_()).collectFirst { case true => true }.getOrElse(false)
  }

}
