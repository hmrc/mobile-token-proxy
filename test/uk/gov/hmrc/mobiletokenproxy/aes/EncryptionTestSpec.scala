/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.mobiletokenproxy.aes

import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base64
import org.scalatest.{Matchers, WordSpecLike}

class EncryptionTestSpec extends Matchers with WordSpecLike {

  "Using the Java libraries for AES" should {
    "successfully encrypt and decrypt" in {
      val encodedBase64Key = Base64.encodeBase64String(Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
      val key = Base64.decodeBase64(encodedBase64Key)
      val secretKey = new SecretKeySpec(key, "AES")
      val encrypter = new Encrypter(secretKey)
      val decrypter = new Decrypter(secretKey)

      val original = "some_server_token"
      val encrypted = encrypter.encrypt(original)
      val decrypted = decrypter.decrypt(encrypted)

      decrypted shouldBe original

      val decryptedCheck = decrypter.decrypt(encrypted)
      decryptedCheck shouldBe original
    }
  }

}
