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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(new byte[37]));
    }

    @Test
    void rejects_empty_input_as_too_short() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(new byte[0]))
          .withMessageContaining("too short");
    }

    @Test
    void rejects_a_single_byte_as_too_short() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(new byte[] {0x4A}))
          .withMessageContaining("too short");
    }

    @Test
    void rejects_null_input() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatNullPointerException().isThrownBy(() -> codec.decode(null));
    }

    @Test
    void encode_rejects_null_input() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatNullPointerException().isThrownBy(() -> codec.encode(null));
    }

    @Test
    void rejects_a_short_message_with_valid_magic_on_length() {
      byte[] shortMessage = new byte[10];
      shortMessage[0] = 0x4A;
      shortMessage[1] = 0x43;
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(shortMessage))
          .withMessageContaining("too short");
    }

    @ParameterizedTest(name = "byte {0} set to {1} is rejected with a message naming the {2}")
    @CsvSource({"0, 0x00, magic", "2, 0x02, version", "3, 0x7F, algorithm"})
    void rejects_a_corrupted_header_field_with_a_structural_message(
        int index, int value, String stage) {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      message[index] = (byte) value;
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessageContaining(stage);
    }

    @Test
    void rejects_a_key_id_length_that_overruns_the_buffer() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      message[4] = (byte) 0xFF;
      message[5] = (byte) 0xFF;
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class).isThrownBy(() -> codec.decode(message));
    }

    @Test
    void a_two_byte_message_with_bad_magic_is_rejected_on_magic_not_length() {
      // At length exactly 2, the `bytes.length < 2` too-short check must NOT fire (2 < 2 is
      // false), so the bad-magic check below it is reached instead; kills a `< 2` -> `<= 2`
      // boundary mutant on that check.
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(new byte[] {0x00, 0x00}))
          .withMessageContaining("magic");
    }

    @Test
    void a_message_at_exactly_the_minimum_length_passes_the_length_check() {
      // 38 bytes is FIXED_HEADER_LENGTH(20) + 1 + 1 + TAG_LENGTH_BYTES(16), the minimum valid
      // message length. At exactly this length, the `bytes.length < MIN_MESSAGE_LENGTH` check
      // must NOT fire, so decode proceeds to the keyId-length check below it instead; kills a
      // `<` -> `<=` boundary mutant on that check.
      byte[] message = new byte[38];
      message[0] = 0x4A;
      message[1] = 0x43;
      message[2] = 0x01; // format version
      message[3] = 0x01; // AES-256-GCM
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessageContaining("keyId");
    }

    @Test
    void a_one_byte_key_id_round_trips() {
      // Exercises keyIdLength == 1, the lower boundary of the keyId-length validity check; kills
      // a `keyIdLength < 1` -> `<= 1` boundary mutant, which would reject this as invalid.
      byte[] kek = new byte[32];
      java.util.Arrays.fill(kek, (byte) 7);
      JceDataKeyProvider oneCharProvider =
          new JceDataKeyProvider("k", Map.of("k", new SecretKeySpec(kek, "AES")));
      EnvelopeCodec codec = EnvelopeCodec.builder(oneCharProvider).build();
      byte[] plaintext = "x".getBytes(UTF_8);
      assertThat(codec.decode(codec.encode(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void a_message_with_just_enough_bytes_for_the_wrapped_length_field_reads_it() {
      // bytes.length (39) is exactly 6 + keyIdLength(31) + 2 (the wrapped-length field's own two
      // bytes, with nothing left over for a payload) and still clears MIN_MESSAGE_LENGTH(38). The
      // buffer-overrun check on the keyId length must NOT fire (39 > 39 is false), so decode
      // proceeds to read a wrappedLength of 0 and reject on that check instead; kills a `>` ->
      // `>=` boundary mutant on the keyId-length check.
      byte[] message = new byte[39];
      message[0] = 0x4A;
      message[1] = 0x43;
      message[2] = 0x01;
      message[3] = 0x01;
      message[4] = 0;
      message[5] = 31; // keyIdLength = 31
      for (int i = 6; i < 37; i++) {
        message[i] = 'a';
      }
      // bytes 37..38 (wrappedLength) left as 0 -> invalid
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessageContaining("wrapped key length");
    }

    @Test
    void a_key_id_length_far_exceeding_the_buffer_is_rejected_not_wrapped_around() {
      // A keyIdLength of 100 against a 40-byte buffer (which still clears
      // MIN_MESSAGE_LENGTH(38)) must be rejected at the keyId-length check. A
      // `6 + keyIdLength + 2` -> `6 - keyIdLength + 2` mutant would make that check pass instead
      // (a large negative number is never > bytes.length), so decode would instead crash reading
      // past the end of the buffer while looking for the wrapped-length field -- an
      // ArrayIndexOutOfBoundsException instead of the documented DecryptionException.
      byte[] message = new byte[40];
      message[0] = 0x4A;
      message[1] = 0x43;
      message[2] = 0x01;
      message[3] = 0x01;
      message[4] = 0;
      message[5] = 100; // keyIdLength = 100, far larger than the 40-byte buffer
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessageContaining("keyId");
    }

    @Test
    void a_key_id_length_check_that_just_barely_overruns_the_buffer_is_rejected() {
      // keyIdLength=34, bytes.length=39: 6+34+2=42 > 39 must throw here. A trailing `+2` -> `-2`
      // mutant on this same expression computes 6+34-2=38, which is NOT > 39, letting decode fall
      // through to read the wrapped-length field two bytes past the end of the 39-byte buffer --
      // an ArrayIndexOutOfBoundsException instead of the documented DecryptionException. This is
      // deliberately a different keyIdLength/bytes.length pairing from the "far exceeding" test
      // above, which is dominated by the leading `6 + keyIdLength` term and does not distinguish
      // this trailing `+2`.
      byte[] message = new byte[39];
      message[0] = 0x4A;
      message[1] = 0x43;
      message[2] = 0x01;
      message[3] = 0x01;
      message[4] = 0;
      message[5] = 34; // keyIdLength = 34
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessageContaining("keyId");
    }

    @Test
    void a_minimal_one_byte_wrapped_length_passes_the_length_check() {
      // wrappedLength == 1 is the lower boundary of the wrapped-length validity check. It must NOT
      // be rejected there (1 < 1 is false), so decode proceeds to attempt the unwrap and fails
      // with the uniform cryptographic-failure message instead; kills a `wrappedLength < 1` ->
      // `<= 1` boundary mutant, which would instead reject with "invalid wrapped key length".
      byte[] message = new byte[50];
      message[0] = 0x4A;
      message[1] = 0x43;
      message[2] = 0x01;
      message[3] = 0x01;
      message[4] = 0;
      message[5] = 3; // keyIdLength = 3
      message[6] = 'k';
      message[7] = 'e';
      message[8] = 'k';
      message[9] = 0;
      message[10] = 1; // wrappedLength = 1
      message[11] = 5; // 1-byte wrapped payload
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessage("Unable to decrypt data");
    }

    @Test
    void a_wrapped_length_that_overruns_the_buffer_is_rejected_precisely() {
      // headerLength(41) + TAG_LENGTH_BYTES(16) = 57 > bytes.length(40) is deliberately close: a
      // `headerLength + TAG_LENGTH_BYTES` -> `headerLength - TAG_LENGTH_BYTES` mutant computes 25,
      // which is NOT > 40, so decode would proceed past this check instead of rejecting here.
      // bytes.length(40) still clears MIN_MESSAGE_LENGTH(38).
      byte[] message = new byte[40];
      message[0] = 0x4A;
      message[1] = 0x43;
      message[2] = 0x01;
      message[3] = 0x01;
      message[4] = 0;
      message[5] = 1; // keyIdLength = 1
      message[6] = 'k';
      message[7] = 0;
      message[8] = 20; // wrappedLength = 20 -> headerLength = 20 + 1 + 20 = 41
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessageContaining("wrapped key length");
    }

    @Test
    void the_key_id_length_field_is_read_big_endian() {
      // bytes[4]=1, bytes[5]=0 is 256 read big-endian ((b0 << 8) | b1), but 1 read big-endian with
      // shift-right instead of shift-left. Asserting the exact number in the message kills a
      // "Replaced Shift Left with Shift Right" mutant in readUint16.
      byte[] message = new byte[38];
      message[0] = 0x4A;
      message[1] = 0x43;
      message[2] = 0x01;
      message[3] = 0x01;
      message[4] = 1;
      message[5] = 0; // keyIdLength = 256 if read big-endian
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessage("invalid keyId length: 256");
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
      EnvelopeCodec codec = EnvelopeCodec.builder(denying).build();
      assertThatExceptionOfType(DecryptionException.class).isThrownBy(() -> codec.decode(message));
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
      EnvelopeCodec codec = EnvelopeCodec.builder(flaky).build();
      assertThatExceptionOfType(KeyAccessException.class)
          .isThrownBy(() -> codec.decode(message))
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
      EnvelopeCodec codec = EnvelopeCodec.builder(wrongKeys).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessage("Unable to decrypt data");
    }
  }

  /**
   * Returns the message of the {@link DecryptionException} thrown when {@code keyId} is denied by
   * the builder's {@code allowedKeyIds} predicate, so the sanitized echo of {@code keyId} can be
   * asserted directly.
   */
  private static String rejectionMessageFor(String keyId) {
    byte[] dekBytes = new byte[32];
    java.util.Arrays.fill(dekBytes, (byte) 3);
    DataKeyProvider malicious =
        new DataKeyProvider() {
          @Override
          public DataKey newDataKey() {
            return new DataKey(keyId, new SecretKeySpec(dekBytes, "AES"), new byte[] {1, 2, 3, 4});
          }

          @Override
          public SecretKey unwrap(String otherKeyId, byte[] wrapped) {
            throw new UnsupportedOperationException();
          }
        };
    byte[] message = EnvelopeCodec.builder(malicious).build().encode(new byte[] {1});
    EnvelopeCodec restrictive = EnvelopeCodec.builder(malicious).allowedKeyIds(id -> false).build();
    try {
      restrictive.decode(message);
      throw new AssertionError("expected a DecryptionException");
    } catch (DecryptionException e) {
      return e.getMessage();
    }
  }

  @Nested
  class Key_id_sanitization {

    @Test
    void printable_characters_survive_sanitization_verbatim() {
      assertThat(rejectionMessageFor("abc 123!?")).isEqualTo("keyId is not allowed: abc 123!?");
    }

    @Test
    void a_space_character_is_not_treated_as_a_control_character() {
      assertThat(rejectionMessageFor(" x")).isEqualTo("keyId is not allowed:  x");
    }

    @Test
    void the_delete_character_is_replaced_with_a_placeholder() {
      assertThat(rejectionMessageFor("a" + (char) 0x7F + "b"))
          .isEqualTo("keyId is not allowed: a?b");
    }

    @Test
    void control_characters_below_space_are_replaced_with_a_placeholder() {
      assertThat(rejectionMessageFor("a\u0001b")).isEqualTo("keyId is not allowed: a?b");
    }

    @Test
    void c1_control_line_separator_and_bidi_override_are_each_replaced_with_a_placeholder() {
      String keyId = "a\u0085b\u2028c\u202Ed";

      String message = rejectionMessageFor(keyId);

      assertThat(message)
          .isEqualTo("keyId is not allowed: a?b?c?d")
          .doesNotContain("\u0085")
          .doesNotContain("\u2028")
          .doesNotContain("\u202E");
    }
  }
}
