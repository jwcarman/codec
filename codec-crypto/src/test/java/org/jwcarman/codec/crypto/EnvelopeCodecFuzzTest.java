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

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;

/**
 * Fuzz targets for the decode path. In the normal test run Jazzer replays the committed seed corpus
 * (regression mode); with JAZZER_FUZZ=1 (the {@code fuzz} profile) it fuzzes for real.
 */
class EnvelopeCodecFuzzTest {

  private static EnvelopeCodec codec() {
    byte[] kek = new byte[32];
    java.util.Arrays.fill(kek, (byte) 3);
    return EnvelopeCodec.builder(
            new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES"))))
        .build();
  }

  @FuzzTest(maxDuration = "120s")
  void decode_only_throws_the_documented_exceptions(byte[] input) {
    try {
      codec().decode(input);
    } catch (DecryptionException | KeyAccessException expected) {
      // documented outcomes
    }
    // any other Throwable escapes and Jazzer records it as a finding
  }

  @FuzzTest(maxDuration = "120s")
  void mutated_ciphertext_is_rejected_and_unmutated_round_trips(FuzzedDataProvider data) {
    EnvelopeCodec codec = codec();
    byte[] plaintext = data.consumeBytes(256);
    byte[] message = codec.encode(plaintext);
    int flips = data.consumeInt(0, 4);
    boolean mutated = false;
    for (int i = 0; i < flips && message.length > 0; i++) {
      int index = data.consumeInt(0, message.length - 1);
      byte mask = data.consumeByte();
      if (mask != 0) {
        message[index] ^= mask;
        mutated = true;
      }
    }
    if (!mutated) {
      assertThat(codec.decode(message)).isEqualTo(plaintext);
      return;
    }
    try {
      byte[] out = codec.decode(message);
      // A mutation that is accepted must still yield the original plaintext — anything else is a
      // forgery.
      assertThat(out).isEqualTo(plaintext);
    } catch (DecryptionException expected) {
      // documented outcome
    }
  }
}
