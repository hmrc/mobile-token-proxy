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
