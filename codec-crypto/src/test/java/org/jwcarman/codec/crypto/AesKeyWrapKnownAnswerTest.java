/*
 * Copyright © 2026 James Carman
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
package org.jwcarman.codec.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** RFC 3394 §4.6 known-answer vector driven through JceDataKeyProvider's wrap path. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AesKeyWrapKnownAnswerTest {

  private static final HexFormat HEX = HexFormat.of();
  private static final String KEK =
      "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
  private static final String KEY_DATA =
      "00112233445566778899AABBCCDDEEFF000102030405060708090A0B0C0D0E0F";
  private static final String EXPECTED =
      "28C9F404C4B810F4CBCCB35CFB87F8263F5786E2D80ED326CBC7F0E71A99F43BFB988B9B7A02DD21";

  @Test
  void wrapping_the_rfc_3394_key_data_under_the_rfc_kek_yields_the_published_ciphertext() {
    byte[] keyData = HEX.parseHex(KEY_DATA);
    SecureRandom fixed =
        new SecureRandom() {
          @Override
          public void nextBytes(byte[] bytes) {
            System.arraycopy(keyData, 0, bytes, 0, bytes.length);
          }
        };
    JceDataKeyProvider provider =
        JceDataKeyProvider.builder(
                "kek", Map.of("kek", new SecretKeySpec(HEX.parseHex(KEK), "AES")))
            .secureRandom(fixed)
            .build();
    byte[] blob = provider.newDataKey().wrapped();
    assertThat(blob[0]).isEqualTo(JceDataKeyProvider.WRAP_SCHEME_AES_KW);
    assertThat(HEX.formatHex(Arrays.copyOfRange(blob, 1, blob.length)))
        .isEqualToIgnoringCase(EXPECTED);
  }

  @Test
  void the_published_ciphertext_unwraps_to_the_rfc_key_data() {
    JceDataKeyProvider provider =
        new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(HEX.parseHex(KEK), "AES")));
    byte[] payload = HEX.parseHex(EXPECTED);
    byte[] blob = new byte[1 + payload.length];
    blob[0] = JceDataKeyProvider.WRAP_SCHEME_AES_KW;
    System.arraycopy(payload, 0, blob, 1, payload.length);
    assertThat(HEX.formatHex(provider.unwrap("kek", blob).getEncoded()))
        .isEqualToIgnoringCase(KEY_DATA);
  }
}
