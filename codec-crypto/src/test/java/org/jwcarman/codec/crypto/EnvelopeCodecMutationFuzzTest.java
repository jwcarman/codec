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

/**
 * Fuzz target for the encode-then-mutate-then-decode path. In the normal test run Jazzer replays
 * the committed seed corpus (regression mode); with JAZZER_FUZZ=1 (the {@code fuzz} profile) it
 * fuzzes for real.
 *
 * <p>Kept in its own class, separate from {@link EnvelopeCodecDecodeFuzzTest}, because jazzer-junit
 * 0.24.0 fuzzes only the first {@code @FuzzTest} method it finds per JVM; the {@code fuzz} profile
 * runs each class in its own forked JVM ({@code reuseForks=false}) so both targets actually fuzz.
 */
class EnvelopeCodecMutationFuzzTest {

  @FuzzTest(maxDuration = "120s")
  void mutated_ciphertext_is_rejected_and_unmutated_round_trips(FuzzedDataProvider data) {
    EnvelopeCodec codec = EnvelopeCodecFuzzSupport.codec();
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
    } catch (DecryptionException _) {
      // documented outcome
    }
  }
}
