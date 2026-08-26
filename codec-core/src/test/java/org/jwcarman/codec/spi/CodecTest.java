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
package org.jwcarman.codec.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CodecTest {

  private static final Codec<String> UTF8 =
      new Codec<>() {
        @Override
        public byte[] encode(String value) {
          return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String decode(byte[] bytes) {
          return new String(bytes, StandardCharsets.UTF_8);
        }
      };

  private static Codec<byte[]> appending(byte marker) {
    return new Codec<>() {
      @Override
      public byte[] encode(byte[] value) {
        byte[] result = Arrays.copyOf(value, value.length + 1);
        result[value.length] = marker;
        return result;
      }

      @Override
      public byte[] decode(byte[] bytes) {
        return Arrays.copyOf(bytes, bytes.length - 1);
      }
    };
  }

  @Nested
  class And_then {

    @Test
    void applies_transform_after_encoding() {
      Codec<String> composed = UTF8.andThen(appending((byte) 'X'));

      byte[] encoded = composed.encode("hi");

      assertThat(encoded).isEqualTo(new byte[] {'h', 'i', 'X'});
    }

    @Test
    void inverts_transform_before_decoding() {
      Codec<String> composed = UTF8.andThen(appending((byte) 'X'));

      String decoded = composed.decode(new byte[] {'h', 'i', 'X'});

      assertThat(decoded).isEqualTo("hi");
    }

    @Test
    void chains_transforms_outward_on_encode_and_inward_on_decode() {
      Codec<String> composed = UTF8.andThen(appending((byte) 'A')).andThen(appending((byte) 'B'));

      byte[] encoded = composed.encode("hi");

      assertThat(encoded).isEqualTo(new byte[] {'h', 'i', 'A', 'B'});
      assertThat(composed.decode(encoded)).isEqualTo("hi");
    }

    @Test
    void rejects_null_transform() {
      assertThatNullPointerException().isThrownBy(() -> UTF8.andThen(null));
    }
  }

  @Nested
  class Xmap {

    private final Codec<UUID> uuids = UTF8.xmap(UUID::fromString, UUID::toString);

    @Test
    void encodes_through_the_backward_conversion() {
      UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

      assertThat(uuids.encode(id)).isEqualTo(UTF8.encode("123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void decodes_through_the_forward_conversion() {
      UUID id = UUID.randomUUID();

      assertThat(uuids.decode(UTF8.encode(id.toString()))).isEqualTo(id);
    }

    @Test
    void exceptions_from_the_conversions_propagate_unchanged() {
      byte[] notAUuid = UTF8.encode("nope");

      assertThatIllegalArgumentException().isThrownBy(() -> uuids.decode(notAUuid));
    }

    @Test
    void composes_with_and_then() {
      Codec<UUID> composed = uuids.andThen(appending((byte) 'X'));
      UUID id = UUID.randomUUID();

      byte[] encoded = composed.encode(id);

      assertThat(encoded[encoded.length - 1]).isEqualTo((byte) 'X');
      assertThat(composed.decode(encoded)).isEqualTo(id);
    }

    @Test
    void rejects_null_conversions() {
      assertThatNullPointerException().isThrownBy(() -> UTF8.xmap(null, UUID::toString));
      assertThatNullPointerException().isThrownBy(() -> UTF8.xmap(UUID::fromString, null));
    }
  }
}
