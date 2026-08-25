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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JceDataKeyProviderTest {

  private static SecretKey aesKey(byte fill) {
    byte[] bytes = new byte[32];
    java.util.Arrays.fill(bytes, fill);
    return new SecretKeySpec(bytes, "AES");
  }

  @Nested
  class Construction {
    @Test
    void rejects_a_current_key_id_absent_from_the_map() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new JceDataKeyProvider("missing", Map.of("kek", aesKey((byte) 1))));
    }

    @Test
    void rejects_an_empty_kek_map() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new JceDataKeyProvider("kek", Map.of()));
    }

    @Test
    void rejects_a_non_aes_kek() {
      SecretKey hmac = new SecretKeySpec(new byte[32], "HmacSHA256");
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new JceDataKeyProvider("kek", Map.of("kek", hmac)));
    }

    @Test
    void rejects_a_16_byte_aes_kek() {
      SecretKey shortKek = new SecretKeySpec(new byte[16], "AES");
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new JceDataKeyProvider("kek", Map.of("kek", shortKek)));
    }

    @Test
    void accepts_an_opaque_aes_kek_with_no_encoded_form() {
      SecretKey opaque =
          new SecretKey() {
            @Override
            public String getAlgorithm() {
              return "AES";
            }

            @Override
            public String getFormat() {
              return null;
            }

            @Override
            public byte[] getEncoded() {
              return null;
            }
          };
      assertThat(new JceDataKeyProvider("kek", Map.of("kek", opaque))).isNotNull();
    }
  }

  @Nested
  class Round_tripping {

    // Equivalent mutant: PIT's "removed call to java/util/Arrays::fill" mutant on the
    // `Arrays.fill(dekBytes, (byte) 0)` line in JceDataKeyProvider.newDataKey() is undetectable by
    // any test. dekBytes is a local array that is never read again after the SecretKeySpec copies
    // its contents, never returned, and never aliased elsewhere; zeroing it is a defense-in-depth
    // hygiene measure against the bytes lingering in the heap, not a behavior any caller can
    // observe. No sequence of public-API calls can tell whether that line ran.

    @Test
    void a_generated_data_key_unwraps_to_the_same_key_material() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      DataKey dk = provider.newDataKey();
      SecretKey unwrapped = provider.unwrap(dk.keyId(), dk.wrapped());
      assertThat(unwrapped.getEncoded()).isEqualTo(dk.key().getEncoded());
      assertThat(dk.keyId()).isEqualTo("kek");
    }

    @Test
    void unwrap_resolves_historical_keks_while_wrapping_under_the_current_one() {
      JceDataKeyProvider old = new JceDataKeyProvider("old", Map.of("old", aesKey((byte) 1)));
      DataKey wrappedUnderOld = old.newDataKey();
      JceDataKeyProvider rotated =
          new JceDataKeyProvider("new", Map.of("old", aesKey((byte) 1), "new", aesKey((byte) 2)));
      assertThat(rotated.newDataKey().keyId()).isEqualTo("new");
      assertThat(rotated.unwrap("old", wrappedUnderOld.wrapped()).getEncoded())
          .isEqualTo(wrappedUnderOld.key().getEncoded());
    }

    @Test
    void generated_deks_are_256_bit_and_distinct() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      DataKey a = provider.newDataKey();
      DataKey b = provider.newDataKey();
      assertThat(a.key().getEncoded()).hasSize(32);
      assertThat(a.key().getEncoded()).isNotEqualTo(b.key().getEncoded());
    }

    @Test
    void an_injected_secure_random_makes_dek_generation_deterministic() {
      JceDataKeyProvider a =
          new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)), fixedRandom());
      JceDataKeyProvider b =
          new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)), fixedRandom());
      assertThat(a.newDataKey().key().getEncoded()).isEqualTo(b.newDataKey().key().getEncoded());
    }

    private static SecureRandom fixedRandom() {
      return new SecureRandom() {
        private byte next = 0;

        @Override
        public void nextBytes(byte[] bytes) {
          for (int i = 0; i < bytes.length; i++) {
            bytes[i] = next++;
          }
        }
      };
    }
  }

  @Nested
  class Admission_and_rejection {
    @Test
    void allows_only_key_ids_in_the_map() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      assertThat(provider.allowsKeyId("kek")).isTrue();
      assertThat(provider.allowsKeyId("other")).isFalse();
    }

    @Test
    void unwrap_of_an_unknown_key_id_is_a_decryption_exception_with_the_uniform_message() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> provider.unwrap("other", new byte[] {1, 2, 3, 4, 5, 6, 7, 8}))
          .withMessage("Unable to decrypt data");
    }

    @Test
    void unwrap_of_a_tampered_blob_is_a_decryption_exception_with_the_uniform_message() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      byte[] wrapped = provider.newDataKey().wrapped();
      wrapped[1] ^= 1;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> provider.unwrap("kek", wrapped))
          .withMessage("Unable to decrypt data");
    }
  }

  @Nested
  class Wrap_scheme {
    @Test
    void wrapped_blob_carries_the_aes_kw_scheme_tag_and_is_41_bytes() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      byte[] wrapped = provider.newDataKey().wrapped();
      assertThat(wrapped).hasSize(41);
      assertThat(wrapped[0]).isEqualTo(JceDataKeyProvider.WRAP_SCHEME_AES_KW);
    }

    @Test
    void an_unknown_scheme_tag_is_rejected_with_the_uniform_message() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      byte[] wrapped = provider.newDataKey().wrapped();
      wrapped[0] = 0x7F;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> provider.unwrap("kek", wrapped))
          .withMessage("Unable to decrypt data");
    }

    @Test
    void a_one_byte_blob_is_rejected_with_the_uniform_message() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(
              () -> provider.unwrap("kek", new byte[] {JceDataKeyProvider.WRAP_SCHEME_AES_KW}))
          .withMessage("Unable to decrypt data");
    }

    @Test
    void a_two_byte_blob_with_a_valid_scheme_tag_reaches_the_cipher_not_the_length_guard() {
      // At exactly 2 bytes (scheme tag + 1 payload byte), the length guard must let this through
      // to the cipher, which then rejects the malformed 1-byte AESWrap payload with a
      // GeneralSecurityException cause. A `wrapped.length < 2` boundary mutated to `<= 2` would
      // instead reject this blob at the guard itself, with a null cause.
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(
              () -> provider.unwrap("kek", new byte[] {JceDataKeyProvider.WRAP_SCHEME_AES_KW, 0}))
          .withMessage("Unable to decrypt data")
          .withCauseInstanceOf(GeneralSecurityException.class);
    }
  }
}
