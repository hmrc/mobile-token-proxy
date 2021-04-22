/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.mobiletokenproxy.aes;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.Key;

class Decrypter {

    private Key key;

    protected Decrypter() {
    }

    protected Decrypter(Key key) {
        this.key = key;
    }

    protected String decrypt(String data) {
        validateKey();
        return decrypt(data, key);
    }

    protected byte[] decryptAsBytes(String data) {
        validateKey();
        return decryptAsBytes(data, key);
    }

    private void validateKey() {
        if (key == null) throw new IllegalStateException("There is no Key defined for this Decrypter");
    }

    protected String decrypt(String data, Key key) {
        return decrypt(data, key, key.getAlgorithm());
    }

    protected byte[] decryptAsBytes(String data, Key key) {
        return decryptAsBytes(data, key, key.getAlgorithm());
    }

    protected String decrypt(String data, Key key, String algorithm) {
        return new String(decryptAsBytes(data, key, algorithm));
    }

    protected byte[] decryptAsBytes(String data, Key key, String algorithm) {
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.DECRYPT_MODE, key, cipher.getParameters());
            return cipher.doFinal(Base64.decodeBase64(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new SecurityException("Failed decrypting data", e);
        }
    }
}