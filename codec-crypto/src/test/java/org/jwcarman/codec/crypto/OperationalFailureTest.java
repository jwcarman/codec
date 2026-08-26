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

import java.util.Arrays;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The failure paths that only a misbehaving provider or key can reach. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OperationalFailureTest {

  private static final byte[] PLAINTEXT = "hello".getBytes(UTF_8);

  private static Map<String, SecretKey> keks() {
    byte[] kek = new byte[32];
    Arrays.fill(kek, (byte) 9);
    return Map.of("kek", new SecretKeySpec(kek, "AES"));
  }

  private static SecretKey aes256() {
    byte[] raw = new byte[32];
    Arrays.fill(raw, (byte) 3);
    return new SecretKeySpec(raw, "AES");
  }

  private static DataKeyProvider providerOf(SecretKey key) {
    return new DataKeyProvider() {
      @Override
      public DataKey newDataKey() {
        return new DataKey("kek", key, new byte[] {1});
      }

      @Override
      public SecretKey unwrap(String keyId, byte[] wrapped) {
        return key;
      }

      @Override
      public boolean allowsKeyId(String keyId) {
        return true;
      }
    };
  }

  /** An AES key whose material is opaque, as an HSM-backed key would be. */
  private static SecretKey opaqueAesKey() {
    return new SecretKey() {
      private static final long serialVersionUID = 1L;

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
  }

  @Nested
  class Provider_description {
    @Test
    void a_null_provider_is_described_as_the_default() {
      assertThat(Providers.describe(null)).isEqualTo("<default>");
    }

    @Test
    void a_provider_is_described_by_name() {
      assertThat(Providers.describe(new FailingProvider())).isEqualTo("Failing");
    }
  }

  @Nested
  class Wrap_failure {
    @Test
    void a_provider_that_fails_to_wrap_surfaces_as_an_encryption_exception() {
      JceDataKeyProvider keys =
          JceDataKeyProvider.builder("kek", keks()).provider(new FailingProvider()).build();

      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(keys::newDataKey)
          .withMessage("Unable to wrap data key")
          .withCauseInstanceOf(java.security.InvalidKeyException.class);
    }
  }

  @Nested
  class Encrypt_failure {
    @Test
    void a_provider_that_fails_to_encrypt_surfaces_as_an_encryption_exception() {
      EnvelopeCodec codec =
          EnvelopeCodec.builder(providerOf(aes256())).provider(new FailingProvider()).build();

      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(() -> codec.encode(PLAINTEXT))
          .withMessage("Unable to encrypt data")
          .withCauseInstanceOf(javax.crypto.BadPaddingException.class);
    }

    @Test
    void a_strategy_that_throws_an_unrelated_runtime_exception_is_wrapped() {
      DataKeyStrategy broken =
          provider -> {
            throw new IllegalStateException("key service down");
          };
      EnvelopeCodec codec = EnvelopeCodec.builder(providerOf(aes256())).strategy(broken).build();

      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(() -> codec.encode(PLAINTEXT))
          .withMessage("Unable to acquire data key")
          .withCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_strategy_that_throws_an_encryption_exception_is_passed_through() {
      EncryptionException original = new EncryptionException("already typed", null);
      DataKeyStrategy broken =
          provider -> {
            throw original;
          };
      EnvelopeCodec codec = EnvelopeCodec.builder(providerOf(aes256())).strategy(broken).build();

      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(() -> codec.encode(PLAINTEXT))
          .isSameAs(original);
    }
  }

  @Nested
  class Data_key_validation {
    @Test
    void a_non_aes_data_key_is_rejected_before_encryption() {
      SecretKey hmac = new SecretKeySpec(new byte[32], "HmacSHA256");
      EnvelopeCodec codec = EnvelopeCodec.builder(providerOf(hmac)).build();

      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(() -> codec.encode(PLAINTEXT))
          .withMessageContaining("expected AES, got HmacSHA256");
    }

    @Test
    void an_aes_key_of_the_wrong_length_is_rejected_before_encryption() {
      SecretKey aes128 = new SecretKeySpec(new byte[16], "AES");
      EnvelopeCodec codec = EnvelopeCodec.builder(providerOf(aes128)).build();

      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(() -> codec.encode(PLAINTEXT))
          .withMessageContaining("expected 32 bytes");
    }

    @Test
    void an_opaque_aes_key_passes_validation_and_is_handed_to_the_provider() {
      // Its length cannot be checked, so validation trusts it; the JDK provider then rejects the
      // keyless material, which surfaces as an encryption failure rather than a validation one.
      EnvelopeCodec codec = EnvelopeCodec.builder(providerOf(opaqueAesKey())).build();

      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(() -> codec.encode(PLAINTEXT))
          .withMessage("Unable to encrypt data");
    }
  }
}
