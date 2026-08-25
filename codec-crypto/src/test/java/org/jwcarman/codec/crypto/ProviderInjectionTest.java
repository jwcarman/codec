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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.security.Provider;
import java.security.Security;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProviderInjectionTest {

  private static final Provider SUN_JCE = Security.getProvider("SunJCE");

  /** A provider that registers no services at all: every getInstance against it fails. */
  private static final class EmptyProvider extends Provider {
    private static final long serialVersionUID = 1L;

    EmptyProvider() {
      super("Empty", "1.0", "registers nothing");
    }
  }

  private static Map<String, SecretKey> keks() {
    byte[] kek = new byte[32];
    java.util.Arrays.fill(kek, (byte) 5);
    return Map.of("kek", new SecretKeySpec(kek, "AES"));
  }

  @Nested
  class Explicit_provider {
    @Test
    void sun_jce_selected_explicitly_round_trips_through_both_seams() {
      JceDataKeyProvider keys = JceDataKeyProvider.builder("kek", keks()).provider(SUN_JCE).build();
      EnvelopeCodec codec = EnvelopeCodec.builder(keys).provider(SUN_JCE).build();
      byte[] plaintext = "explicit provider".getBytes(UTF_8);
      assertThat(codec.decode(codec.encode(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void jce_builder_secure_random_seam_is_honoured() {
      var random =
          new java.security.SecureRandom() {
            private byte next = 9;

            @Override
            public void nextBytes(byte[] bytes) {
              for (int i = 0; i < bytes.length; i++) {
                bytes[i] = next++;
              }
            }
          };
      JceDataKeyProvider a = JceDataKeyProvider.builder("kek", keks()).secureRandom(random).build();
      assertThat(a.newDataKey().key().getEncoded()[0]).isEqualTo((byte) 9);
    }
  }

  @Nested
  class Fail_fast {
    @Test
    void codec_builder_rejects_a_provider_without_aes_gcm() {
      JceDataKeyProvider keys = new JceDataKeyProvider("kek", keks());
      assertThatIllegalStateException()
          .isThrownBy(() -> EnvelopeCodec.builder(keys).provider(new EmptyProvider()).build())
          .withMessageContaining("AES/GCM/NoPadding");
    }

    @Test
    void jce_builder_rejects_a_provider_without_aes_wrap() {
      assertThatIllegalStateException()
          .isThrownBy(
              () -> JceDataKeyProvider.builder("kek", keks()).provider(new EmptyProvider()).build())
          .withMessageContaining("AESWrap");
    }
  }
}
