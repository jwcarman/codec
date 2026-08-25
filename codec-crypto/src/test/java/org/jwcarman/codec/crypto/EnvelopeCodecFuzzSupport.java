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

import java.util.Arrays;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared codec construction for the fuzz targets in {@link EnvelopeCodecDecodeFuzzTest} and {@link
 * EnvelopeCodecMutationFuzzTest}.
 */
final class EnvelopeCodecFuzzSupport {

  private EnvelopeCodecFuzzSupport() {}

  static EnvelopeCodec codec() {
    byte[] kek = new byte[32];
    Arrays.fill(kek, (byte) 3);
    return EnvelopeCodec.builder(
            new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES"))))
        .build();
  }
}
