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

package uk.gov.hmrc.mobiletokenproxy.aes;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.Key;

class Encrypter {

    private Key key;

    protected Encrypter() {
    }

    protected Encrypter(Key key) {
        this.key = key;
    }

    protected String encrypt(String data) {
        validateKey();
        return encrypt(data, key);
    }

    protected String encrypt(byte[] data) {
        validateKey();
        return encrypt(data, key);
    }

    private void validateKey() {
        if (key == null) throw new IllegalStateException("There is no Key defined for this Encrypter");
    }

    protected String encrypt(byte[] data, Key key) {
        return encrypt(data, key, key.getAlgorithm());
    }


    protected String encrypt(String data, Key key) {
        return encrypt(data.getBytes(StandardCharsets.UTF_8), key, key.getAlgorithm());
    }

    protected String encrypt(String data, Key key, String algorithm) {
        return encrypt(data.getBytes(StandardCharsets.UTF_8), key, algorithm);
    }

    protected String encrypt(byte[] data, Key key, String algorithm) {
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, key, cipher.getParameters());
            return new String(Base64.encodeBase64(cipher.doFinal(data)),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("Failed encrypting data", e);
        }
    }
}