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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EnvelopeCodecEncodeTest {

  private static JceDataKeyProvider provider() {
    byte[] kek = new byte[32];
    java.util.Arrays.fill(kek, (byte) 7);
    return new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES")));
  }

  @Nested
  class Builder_validation {
    @Test
    void rejects_an_empty_aad() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).aad(new byte[0]).build());
    }
  }

  @Nested
  class Wire_layout {
    @Test
    void encoded_output_carries_the_documented_header() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode("hello".getBytes(UTF_8));

      assertThat(message[0]).isEqualTo((byte) 0x4A);
      assertThat(message[1]).isEqualTo((byte) 0x43);
      assertThat(message[2]).isEqualTo((byte) 0x01); // version
      assertThat(message[3]).isEqualTo((byte) 0x01); // AES-256-GCM

      int k = ((message[4] & 0xFF) << 8) | (message[5] & 0xFF);
      assertThat(new String(message, 6, k, UTF_8)).isEqualTo("kek");

      int wOffset = 6 + k;
      int w = ((message[wOffset] & 0xFF) << 8) | (message[wOffset + 1] & 0xFF);
      assertThat(w).isEqualTo(40); // AESWrap of a 32-byte DEK

      // header(20 + k + w) + ciphertext(5) + tag(16)
      assertThat(message).hasSize(20 + k + w + 5 + 16);
    }

    @Test
    void empty_plaintext_encodes_to_header_plus_tag_only() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[0]);
      int k = ((message[4] & 0xFF) << 8) | (message[5] & 0xFF);
      int wOffset = 6 + k;
      int w = ((message[wOffset] & 0xFF) << 8) | (message[wOffset + 1] & 0xFF);
      assertThat(message).hasSize(20 + k + w + 16);
    }

    @Test
    void two_encodes_of_the_same_plaintext_differ() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] plaintext = "same".getBytes(UTF_8);
      assertThat(codec.encode(plaintext)).isNotEqualTo(codec.encode(plaintext));
    }
  }

  @Nested
  class Encode_failures {
    @Test
    void a_provider_failure_surfaces_as_an_encryption_exception() {
      DataKeyProvider failing =
          new DataKeyProvider() {
            @Override
            public DataKey newDataKey() {
              throw new RuntimeException("kms down");
            }

            @Override
            public javax.crypto.SecretKey unwrap(String keyId, byte[] wrapped) {
              throw new UnsupportedOperationException();
            }
          };
      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(failing).build().encode(new byte[] {1}))
          .withCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void rejects_null_input() {
      assertThatNullPointerException()
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().encode(null));
    }

    @Test
    void a_16_byte_aes_data_key_is_rejected_as_not_aes_256() {
      DataKeyProvider shortKey =
          new DataKeyProvider() {
            @Override
            public DataKey newDataKey() {
              return new DataKey("kek", new SecretKeySpec(new byte[16], "AES"), new byte[] {1});
            }

            @Override
            public SecretKey unwrap(String keyId, byte[] wrapped) {
              throw new UnsupportedOperationException();
            }
          };
      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(shortKey).build().encode(new byte[] {1}));
    }
  }
}
