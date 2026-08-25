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
import java.util.concurrent.atomic.AtomicInteger;
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

  /** Delegates every service lookup to SunJCE but counts them, so operational use is observable. */
  private static final class CountingProvider extends Provider {
    private static final long serialVersionUID = 1L;

    final AtomicInteger lookups = new AtomicInteger();

    CountingProvider() {
      super("Counting", "1.0", "delegates to SunJCE and counts");
    }

    @Override
    public Service getService(String type, String algorithm) {
      lookups.incrementAndGet();
      return SUN_JCE.getService(type, algorithm);
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
  class Operational_call_sites {
    @Test
    void codec_encode_and_decode_use_the_provider_beyond_the_build_time_probe() {
      CountingProvider counting = new CountingProvider();
      JceDataKeyProvider keys = new JceDataKeyProvider("kek", keks());
      EnvelopeCodec codec = EnvelopeCodec.builder(keys).provider(counting).build();
      int afterBuild = counting.lookups.get();

      byte[] plaintext = "operational call sites".getBytes(UTF_8);
      byte[] encoded = codec.encode(plaintext);
      int afterEncode = counting.lookups.get();
      assertThat(afterEncode - afterBuild).isGreaterThanOrEqualTo(1);

      codec.decode(encoded);
      int afterDecode = counting.lookups.get();
      assertThat(afterDecode - afterEncode).isGreaterThanOrEqualTo(1);
    }

    @Test
    void jce_provider_new_data_key_and_unwrap_use_the_provider_beyond_the_build_time_probe() {
      CountingProvider counting = new CountingProvider();
      JceDataKeyProvider keys =
          JceDataKeyProvider.builder("kek", keks()).provider(counting).build();
      int afterBuild = counting.lookups.get();

      DataKey dataKey = keys.newDataKey();
      int afterNewDataKey = counting.lookups.get();
      assertThat(afterNewDataKey - afterBuild).isGreaterThanOrEqualTo(1);

      keys.unwrap(dataKey.keyId(), dataKey.wrapped());
      int afterUnwrap = counting.lookups.get();
      assertThat(afterUnwrap - afterNewDataKey).isGreaterThanOrEqualTo(1);
    }
  }

  @Nested
  class Fail_fast {
    @Test
    void codec_builder_rejects_a_provider_without_aes_gcm() {
      JceDataKeyProvider keys = new JceDataKeyProvider("kek", keks());
      assertThatIllegalStateException()
          .isThrownBy(() -> EnvelopeCodec.builder(keys).provider(new EmptyProvider()).build())
          .withMessageContaining("AES/GCM/NoPadding")
          // Names the explicitly-supplied provider rather than falling back to "<default>": kills
          // a `provider == null` negated-conditional mutant in checkTransform's message-building.
          .withMessageContaining("Empty");
    }

    @Test
    void jce_builder_rejects_a_provider_without_aes_wrap() {
      assertThatIllegalStateException()
          .isThrownBy(
              () -> JceDataKeyProvider.builder("kek", keks()).provider(new EmptyProvider()).build())
          .withMessageContaining("AES/KW/NoPadding")
          // Names the explicitly-supplied provider rather than falling back to "<default>": kills
          // a `provider == null` negated-conditional mutant in the constructor's message-building.
          .withMessageContaining("Empty");
    }
  }
}
