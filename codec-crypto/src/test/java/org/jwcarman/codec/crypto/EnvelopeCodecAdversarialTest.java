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

import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.transform.GzipCodec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EnvelopeCodecAdversarialTest {

  private static JceDataKeyProvider provider() {
    byte[] kek = new byte[32];
    java.util.Arrays.fill(kek, (byte) 7);
    return new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES")));
  }

  @Nested
  class Tamper_matrix {
    @Test
    void every_single_byte_flip_in_the_message_is_rejected() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] message = codec.encode("tamper target".getBytes(UTF_8));
      for (int i = 0; i < message.length; i++) {
        byte[] mutated = message.clone();
        mutated[i] ^= 0x01;
        int index = i;
        assertThatExceptionOfType(RuntimeException.class)
            .as("flipping byte %d must be rejected", index)
            .isThrownBy(() -> codec.decode(mutated))
            .isInstanceOfAny(DecryptionException.class);
      }
    }

    @Test
    void a_truncated_tag_is_rejected() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] message = codec.encode("x".getBytes(UTF_8));
      byte[] truncated = java.util.Arrays.copyOf(message, message.length - 1);
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(truncated));
    }

    @Test
    void an_appended_trailing_byte_is_rejected() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] message = codec.encode("x".getBytes(UTF_8));
      byte[] extended = java.util.Arrays.copyOf(message, message.length + 1);
      assertThatExceptionOfType(DecryptionException.class).isThrownBy(() -> codec.decode(extended));
    }
  }

  @Nested
  class Aad_matrix {
    @Test
    void ciphertext_bound_to_one_aad_is_rejected_under_another() {
      byte[] message =
          EnvelopeCodec.builder(provider())
              .aad("tenant-1".getBytes(UTF_8))
              .build()
              .encode("x".getBytes(UTF_8));
      EnvelopeCodec other =
          EnvelopeCodec.builder(provider()).aad("tenant-2".getBytes(UTF_8)).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> other.decode(message))
          .withMessage("Unable to decrypt data");
    }

    @Test
    void aad_bound_ciphertext_is_rejected_by_an_aad_less_codec() {
      byte[] message =
          EnvelopeCodec.builder(provider())
              .aad("tenant-1".getBytes(UTF_8))
              .build()
              .encode("x".getBytes(UTF_8));
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class).isThrownBy(() -> codec.decode(message));
    }

    @Test
    void plain_ciphertext_is_rejected_by_an_aad_bound_codec() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode("x".getBytes(UTF_8));
      EnvelopeCodec bound =
          EnvelopeCodec.builder(provider()).aad("tenant-1".getBytes(UTF_8)).build();
      assertThatExceptionOfType(DecryptionException.class).isThrownBy(() -> bound.decode(message));
    }
  }

  @Nested
  class Cross_provider {
    @Test
    void a_different_provider_instance_sharing_the_kek_decodes_the_message() {
      byte[] kek = new byte[32];
      java.util.Arrays.fill(kek, (byte) 7);
      SecretKey shared = new SecretKeySpec(kek, "AES");
      EnvelopeCodec writer =
          EnvelopeCodec.builder(new JceDataKeyProvider("kek", Map.of("kek", shared))).build();
      EnvelopeCodec reader =
          EnvelopeCodec.builder(new JceDataKeyProvider("kek", Map.of("kek", shared))).build();
      byte[] plaintext = "portable".getBytes(UTF_8);
      assertThat(reader.decode(writer.encode(plaintext))).isEqualTo(plaintext);
    }
  }

  @Nested
  class Composition {
    @Test
    void compress_then_encrypt_round_trips_through_and_then() {
      org.jwcarman.codec.spi.Codec<byte[]> chain =
          new GzipCodec().andThen(EnvelopeCodec.builder(provider()).build());
      byte[] plaintext = "the quick brown fox ".repeat(100).getBytes(UTF_8);
      assertThat(chain.decode(chain.encode(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void gzip_output_fed_directly_to_decode_is_rejected_fast_on_magic() {
      byte[] gzipped = new GzipCodec().encode("not encrypted".getBytes(UTF_8));
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(gzipped))
          .withMessageContaining("magic");
    }
  }
}
