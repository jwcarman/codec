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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EnvelopeCodecDecodeTest {

  private static JceDataKeyProvider provider() {
    byte[] kek = new byte[32];
    java.util.Arrays.fill(kek, (byte) 7);
    return new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES")));
  }

  @Nested
  class Round_tripping {
    @Test
    void decode_recovers_the_plaintext() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] plaintext = "round trip".getBytes(UTF_8);
      assertThat(codec.decode(codec.encode(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void empty_plaintext_round_trips() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThat(codec.decode(codec.encode(new byte[0]))).isEmpty();
    }

    @Test
    void aad_bound_ciphertext_round_trips_with_the_same_aad() {
      EnvelopeCodec codec =
          EnvelopeCodec.builder(provider()).aad("tenant-1".getBytes(UTF_8)).build();
      byte[] plaintext = "bound".getBytes(UTF_8);
      assertThat(codec.decode(codec.encode(plaintext))).isEqualTo(plaintext);
    }
  }

  @Nested
  class Structural_rejection {
    @Test
    void rejects_all_zero_input_on_bad_magic() {
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(new byte[37]));
    }

    @Test
    void rejects_empty_input_as_too_short() {
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(new byte[0]))
          .withMessageContaining("too short");
    }

    @Test
    void rejects_a_single_byte_as_too_short() {
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(new byte[] {0x4A}))
          .withMessageContaining("too short");
    }

    @Test
    void rejects_null_input() {
      assertThatNullPointerException()
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(null));
    }

    @Test
    void encode_rejects_null_input() {
      assertThatNullPointerException()
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().encode(null));
    }

    @Test
    void rejects_a_short_message_with_valid_magic_on_length() {
      byte[] shortMessage = new byte[10];
      shortMessage[0] = 0x4A;
      shortMessage[1] = 0x43;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(shortMessage))
          .withMessageContaining("too short");
    }

    @Test
    void rejects_bad_magic_with_a_structural_message() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      message[0] = 0x00;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(message))
          .withMessageContaining("magic");
    }

    @Test
    void rejects_an_unknown_version() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      message[2] = 0x02;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(message))
          .withMessageContaining("version");
    }

    @Test
    void rejects_an_unknown_algorithm() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      message[3] = 0x7F;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(message))
          .withMessageContaining("algorithm");
    }

    @Test
    void rejects_a_key_id_length_that_overruns_the_buffer() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      message[4] = (byte) 0xFF;
      message[5] = (byte) 0xFF;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(message));
    }
  }

  @Nested
  class Admission {
    @Test
    void a_key_id_rejected_by_the_builder_predicate_never_reaches_unwrap() {
      AtomicInteger unwraps = new AtomicInteger();
      JceDataKeyProvider real = provider();
      DataKeyProvider counting =
          new DataKeyProvider() {
            @Override
            public DataKey newDataKey() {
              return real.newDataKey();
            }

            @Override
            public SecretKey unwrap(String keyId, byte[] wrapped) {
              unwraps.incrementAndGet();
              return real.unwrap(keyId, wrapped);
            }
          };
      byte[] message = EnvelopeCodec.builder(counting).build().encode(new byte[] {1});
      EnvelopeCodec restrictive =
          EnvelopeCodec.builder(counting).allowedKeyIds(id -> false).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> restrictive.decode(message));
      assertThat(unwraps).hasValue(0);
    }

    @Test
    void without_a_predicate_admission_falls_to_the_providers_allows_key_id() {
      AtomicInteger unwraps = new AtomicInteger();
      JceDataKeyProvider real = provider();
      DataKeyProvider denying =
          new DataKeyProvider() {
            @Override
            public DataKey newDataKey() {
              return real.newDataKey();
            }

            @Override
            public SecretKey unwrap(String keyId, byte[] wrapped) {
              unwraps.incrementAndGet();
              return real.unwrap(keyId, wrapped);
            }

            @Override
            public boolean allowsKeyId(String keyId) {
              return false;
            }
          };
      byte[] message = EnvelopeCodec.builder(denying).build().encode(new byte[] {1});
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(denying).build().decode(message));
      assertThat(unwraps).hasValue(0);
    }

    @Test
    void the_builder_predicate_overrides_a_permissive_provider() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).allowedKeyIds("kek"::equals).build();
      assertThat(codec.decode(message)).containsExactly(1);
    }

    @Test
    void a_denied_key_ids_echo_in_the_message_is_bounded_and_control_free() {
      String maliciousKeyId = "a".repeat(200) + "\n" + "";
      byte[] dekBytes = new byte[32];
      java.util.Arrays.fill(dekBytes, (byte) 3);
      DataKeyProvider malicious =
          new DataKeyProvider() {
            @Override
            public DataKey newDataKey() {
              return new DataKey(
                  maliciousKeyId, new SecretKeySpec(dekBytes, "AES"), new byte[] {1, 2, 3, 4});
            }

            @Override
            public SecretKey unwrap(String keyId, byte[] wrapped) {
              throw new UnsupportedOperationException();
            }
          };
      byte[] message = EnvelopeCodec.builder(malicious).build().encode(new byte[] {1});
      EnvelopeCodec restrictive =
          EnvelopeCodec.builder(malicious).allowedKeyIds(id -> false).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> restrictive.decode(message))
          .satisfies(
              e -> {
                String msg = e.getMessage();
                assertThat(msg.length()).isLessThanOrEqualTo(100);
                boolean hasControlChar = msg.chars().anyMatch(c -> c < 0x20 || c == 0x7F);
                assertThat(hasControlChar).isFalse();
              });
    }
  }

  @Nested
  class Error_taxonomy {
    @Test
    void a_provider_availability_failure_is_a_key_access_exception_not_decryption() {
      JceDataKeyProvider real = provider();
      DataKeyProvider flaky =
          new DataKeyProvider() {
            @Override
            public DataKey newDataKey() {
              return real.newDataKey();
            }

            @Override
            public SecretKey unwrap(String keyId, byte[] wrapped) {
              throw new RuntimeException("kms timeout");
            }
          };
      byte[] message = EnvelopeCodec.builder(flaky).build().encode(new byte[] {1});
      assertThatExceptionOfType(KeyAccessException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(flaky).build().decode(message))
          .withCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void a_provider_rejection_stays_a_decryption_exception() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      // decode with a provider holding a DIFFERENT kek: unwrap fails cryptographically
      byte[] otherKek = new byte[32];
      java.util.Arrays.fill(otherKek, (byte) 9);
      JceDataKeyProvider wrongKeys =
          new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(otherKek, "AES")));
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(wrongKeys).build().decode(message))
          .withMessage("Unable to decrypt data");
    }
  }
}
