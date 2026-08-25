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

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WireFormatVectorTest {

  /** Deterministic byte source: 0, 1, 2, ... — DEK first (32 bytes), then nonce (12 bytes). */
  private static SecureRandom sequentialRandom() {
    return new SecureRandom() {
      private int next = 0;

      @Override
      public void nextBytes(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
          bytes[i] = (byte) next++;
        }
      }
    };
  }

  private static EnvelopeCodec deterministicCodec() {
    byte[] kek = new byte[32]; // all zeros, deliberately
    SecureRandom random = sequentialRandom();
    return EnvelopeCodec.builder(
            new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES")), random))
        .secureRandom(random)
        .build();
  }

  private static final String FROZEN_VECTOR_HEX =
      "4a43010100036b656b0028246d75815315bd8b40ac141ba1cbca56785ae73cbbafa7fd20f4c91a99312c946edff8132c8259b4202122232425262728292a2bb155c2150fb5797c630c36a1e16ec5fd58ca826105f90dd5de2c473e640feb";

  @Test
  void the_wire_format_matches_the_frozen_vector_byte_for_byte() {
    byte[] message = deterministicCodec().encode("codec-crypto v1".getBytes(UTF_8));
    assertThat(HexFormat.of().formatHex(message)).isEqualTo(FROZEN_VECTOR_HEX);
  }

  @Test
  void the_frozen_vector_still_decodes() {
    byte[] kek = new byte[32];
    EnvelopeCodec reader =
        EnvelopeCodec.builder(
                new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES"))))
            .build();
    byte[] frozen = HexFormat.of().parseHex(FROZEN_VECTOR_HEX);
    assertThat(reader.decode(frozen)).isEqualTo("codec-crypto v1".getBytes(UTF_8));
  }
}
