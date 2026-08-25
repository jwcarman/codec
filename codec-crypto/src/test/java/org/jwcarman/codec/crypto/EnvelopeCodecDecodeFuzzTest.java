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

import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzz target for the decode path. In the normal test run Jazzer replays the committed seed corpus
 * (regression mode); with JAZZER_FUZZ=1 (the {@code fuzz} profile) it fuzzes for real.
 *
 * <p>Kept in its own class, separate from {@link EnvelopeCodecMutationFuzzTest}, because
 * jazzer-junit 0.24.0 fuzzes only the first {@code @FuzzTest} method it finds per JVM; the {@code
 * fuzz} profile runs each class in its own forked JVM ({@code reuseForks=false}) so both targets
 * actually fuzz.
 */
class EnvelopeCodecDecodeFuzzTest {

  @FuzzTest(maxDuration = "120s")
  void decode_only_throws_the_documented_exceptions(byte[] input) {
    try {
      EnvelopeCodecFuzzSupport.codec().decode(input);
    } catch (DecryptionException expected) {
      // documented outcome
    }
    // any other Throwable escapes and Jazzer records it as a finding
  }
}
