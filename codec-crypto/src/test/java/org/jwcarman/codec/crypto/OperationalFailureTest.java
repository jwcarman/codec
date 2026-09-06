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

  /** An AES key whose {@code getEncoded()} returns its own backing array, which the API allows. */
  private static SecretKey aliasingKey(byte[] material) {
    return new SecretKey() {
      private static final long serialVersionUID = 1L;

      @Override
      public String getAlgorithm() {
        return "AES";
      }

      @Override
      public String getFormat() {
        return "RAW";
      }

      @Override
      public byte[] getEncoded() {
        return material;
      }
    };
  }

  /** A key whose accessors fail, as a non-extractable HSM key can. */
  private static SecretKey throwingKey() {
    return new SecretKey() {
      private static final long serialVersionUID = 1L;

      @Override
      public String getAlgorithm() {
        throw new java.security.ProviderException("key handle revoked");
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

    // A provider whose unwrap violates its contract is a key-infrastructure failure, not a
    // rejection of the ciphertext: the data may be fine, so it must surface as KeyAccessException
    // and never as the DecryptionException a pipeline quarantines on.

    @Test
    void an_unwrapped_key_of_the_wrong_length_is_a_key_access_failure_not_a_rejection() {
      EnvelopeCodec codec =
          EnvelopeCodec.builder(unwrappingTo(new SecretKeySpec(new byte[16], "AES"))).build();
      byte[] envelope = codec.encode(PLAINTEXT);

      assertThatExceptionOfType(KeyAccessException.class)
          .isThrownBy(() -> codec.decode(envelope))
          .withMessageContaining("expected 32 bytes");
    }

    @Test
    void an_unwrapped_non_aes_key_is_a_key_access_failure_not_a_rejection() {
      EnvelopeCodec codec =
          EnvelopeCodec.builder(unwrappingTo(new SecretKeySpec(new byte[32], "HmacSHA256")))
              .build();
      byte[] envelope = codec.encode(PLAINTEXT);

      assertThatExceptionOfType(KeyAccessException.class)
          .isThrownBy(() -> codec.decode(envelope))
          .withMessageContaining("expected AES, got HmacSHA256");
    }

    @Test
    void an_unwrapped_null_key_is_a_key_access_failure_not_an_npe() {
      EnvelopeCodec codec = EnvelopeCodec.builder(unwrappingTo(null)).build();
      byte[] envelope = codec.encode(PLAINTEXT);

      assertThatExceptionOfType(KeyAccessException.class)
          .isThrownBy(() -> codec.decode(envelope))
          .withMessageContaining("Data key is null");
    }

    @Test
    void a_control_character_in_an_unwrapped_keys_algorithm_name_is_not_echoed() {
      SecretKey hostile = new SecretKeySpec(new byte[32], "AES\nforged log line");
      EnvelopeCodec codec = EnvelopeCodec.builder(unwrappingTo(hostile)).build();
      byte[] envelope = codec.encode(PLAINTEXT);

      assertThatExceptionOfType(KeyAccessException.class)
          .isThrownBy(() -> codec.decode(envelope))
          .withMessageContaining("got AES?forged log line");
    }

    @Test
    void
        an_opaque_unwrapped_key_passes_validation_and_its_rejection_by_the_cipher_is_not_a_rejection_of_the_data() {
      // Its length cannot be checked, so validation trusts it; the JDK provider then refuses the
      // keyless material at cipher init. That happened before any ciphertext was consulted, so it
      // is a key-infrastructure failure. This is the HSM/KMS path: a non-extractable data key must
      // get past the check, and a provider mismatch must never quarantine records.
      EnvelopeCodec codec = EnvelopeCodec.builder(unwrappingTo(opaqueAesKey())).build();
      byte[] envelope = codec.encode(PLAINTEXT);

      assertThatExceptionOfType(KeyAccessException.class)
          .isThrownBy(() -> codec.decode(envelope))
          .withMessage("Data key is unusable with the cipher provider")
          .withCauseInstanceOf(java.security.InvalidKeyException.class);
    }

    @Test
    void a_key_whose_accessors_throw_is_a_key_access_failure_on_decode() {
      EnvelopeCodec codec = EnvelopeCodec.builder(unwrappingTo(throwingKey())).build();
      byte[] envelope = codec.encode(PLAINTEXT);

      assertThatExceptionOfType(KeyAccessException.class)
          .isThrownBy(() -> codec.decode(envelope))
          .withMessage("Data key could not be inspected")
          .withCauseInstanceOf(java.security.ProviderException.class);
    }

    @Test
    void a_key_whose_accessors_throw_is_an_encryption_failure_on_encode() {
      EnvelopeCodec codec = EnvelopeCodec.builder(providerOf(throwingKey())).build();

      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(() -> codec.encode(PLAINTEXT))
          .withMessage("Data key could not be inspected")
          .withCauseInstanceOf(java.security.ProviderException.class);
    }

    @Test
    void an_admission_predicate_that_throws_is_a_key_access_failure() {
      EnvelopeCodec writer = EnvelopeCodec.builder(providerOf(aes256())).build();
      byte[] envelope = writer.encode(PLAINTEXT);
      EnvelopeCodec reader =
          EnvelopeCodec.builder(providerOf(aes256()))
              .allowedKeyIds(
                  keyId -> {
                    throw new IllegalStateException("policy service down");
                  })
              .build();

      assertThatExceptionOfType(KeyAccessException.class)
          .isThrownBy(() -> reader.decode(envelope))
          .withMessage("Admission check failed")
          .withCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_key_that_exposes_its_backing_array_is_sealed_under_its_real_material() {
      // Key.getEncoded() does not promise a copy. A codec that zeroed what it was handed before
      // sealing would encrypt under the all-zero key. (SunJCE's own GCM decrypt path zeroes the
      // array it gets from getEncoded() after key expansion, so the material is not expected to
      // survive a decode; the property that matters is what the ciphertext was sealed under.)
      byte[] material = new byte[32];
      Arrays.fill(material, (byte) 5);
      EnvelopeCodec codec = EnvelopeCodec.builder(providerOf(aliasingKey(material))).build();

      byte[] envelope = codec.encode(PLAINTEXT);

      assertThat(material).containsOnly((byte) 5);
      EnvelopeCodec zeroKeyReader =
          EnvelopeCodec.builder(providerOf(new SecretKeySpec(new byte[32], "AES"))).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> zeroKeyReader.decode(envelope));
      assertThat(codec.decode(envelope)).isEqualTo(PLAINTEXT);
    }

    /** A provider that issues a valid AES-256 data key but unwraps to whatever it is told. */
    private static DataKeyProvider unwrappingTo(SecretKey unwrapped) {
      return new DataKeyProvider() {
        @Override
        public DataKey newDataKey() {
          return new DataKey("kek", aes256(), new byte[] {1});
        }

        @Override
        public SecretKey unwrap(String keyId, byte[] wrapped) {
          return unwrapped;
        }
      };
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
