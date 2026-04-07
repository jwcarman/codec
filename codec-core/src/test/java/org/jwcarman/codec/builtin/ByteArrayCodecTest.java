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
package org.jwcarman.codec.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ByteArrayCodecTest {

  private final ByteArrayCodec codec = new ByteArrayCodec();

  @Test
  void shouldRoundTripByteArray() {
    byte[] original = {1, 2, 3, 4, 5};
    byte[] encoded = codec.encode(original);
    byte[] decoded = codec.decode(encoded);
    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldReturnDefensiveCopyOnEncode() {
    byte[] original = {1, 2, 3};
    byte[] encoded = codec.encode(original);
    original[0] = 99;
    assertThat(encoded[0]).isEqualTo((byte) 1);
  }

  @Test
  void shouldReturnDefensiveCopyOnDecode() {
    byte[] bytes = {1, 2, 3};
    byte[] decoded = codec.decode(bytes);
    bytes[0] = 99;
    assertThat(decoded[0]).isEqualTo((byte) 1);
  }

  @Test
  void shouldRoundTripEmptyArray() {
    byte[] original = {};
    byte[] encoded = codec.encode(original);
    byte[] decoded = codec.decode(encoded);
    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldReturnByteArrayType() {
    assertThat(codec.type()).isEqualTo(byte[].class);
  }
}
