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
package org.jwcarman.codec.zstd;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.UncheckedIOException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.transform.compress.GzipCodec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ZstdCodecTest {

  private final ZstdCodec codec = new ZstdCodec();

  @Nested
  class Encode {

    @Test
    void produces_a_zstandard_frame() {
      byte[] encoded = codec.encode("hello".getBytes(UTF_8));

      assertThat(encoded).startsWith((byte) 0x28, (byte) 0xB5, (byte) 0x2F, (byte) 0xFD);
    }

    @Test
    void compresses_repetitive_input() {
      byte[] input = "abc".repeat(1000).getBytes(UTF_8);

      assertThat(codec.encode(input)).hasSizeLessThan(input.length);
    }

    @Test
    void a_higher_level_compresses_at_least_as_well() {
      byte[] input = "the quick brown fox jumps over the lazy dog ".repeat(200).getBytes(UTF_8);

      assertThat(new ZstdCodec(19).encode(input))
          .hasSizeLessThanOrEqualTo(new ZstdCodec(1).encode(input).length);
    }
  }

  @Nested
  class Decode {

    @Test
    void round_trips_arbitrary_bytes() {
      byte[] input = "the quick brown fox".getBytes(UTF_8);

      assertThat(codec.decode(codec.encode(input))).isEqualTo(input);
    }

    @Test
    void round_trips_empty_input() {
      assertThat(codec.decode(codec.encode(new byte[0]))).isEmpty();
    }

    @Test
    void round_trips_across_levels() {
      byte[] input = "level-independent".repeat(50).getBytes(UTF_8);

      assertThat(new ZstdCodec(1).decode(new ZstdCodec(19).encode(input))).isEqualTo(input);
    }

    @Test
    void rejects_non_zstandard_input() {
      byte[] notCompressed = "not compressed".getBytes(UTF_8);

      assertThatExceptionOfType(UncheckedIOException.class)
          .isThrownBy(() -> codec.decode(notCompressed));
    }
  }

  @Nested
  class Decoded_size_cap {

    @Test
    void rejects_payloads_expanding_beyond_the_cap() {
      byte[] bomb = codec.encode(new byte[10_000]);
      ZstdCodec capped = new ZstdCodec(3, 1_000);

      assertThatExceptionOfType(IllegalStateException.class)
          .isThrownBy(() -> capped.decode(bomb))
          .withMessageContaining("exceeds the maximum");
    }

    @Test
    void allows_payloads_exactly_at_the_cap() {
      byte[] payload = new byte[1_000];
      byte[] encoded = codec.encode(payload);

      assertThat(new ZstdCodec(3, 1_000).decode(encoded)).isEqualTo(payload);
    }

    @Test
    void rejects_a_non_positive_cap() {
      assertThatIllegalArgumentException().isThrownBy(() -> new ZstdCodec(3, 0));
    }
  }

  @Nested
  class Compression_level_validation {

    @Test
    void rejects_a_level_below_the_library_minimum() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new ZstdCodec(Integer.MIN_VALUE))
          .withMessageContaining("level");
    }

    @Test
    void rejects_a_level_above_the_library_maximum() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new ZstdCodec(Integer.MAX_VALUE))
          .withMessageContaining("level");
    }
  }

  @Nested
  class Composed_with_and_then {

    @Test
    void round_trips_through_another_transform() {
      Codec<byte[]> chain = new ZstdCodec().andThen(new GzipCodec());
      byte[] input = "layered".repeat(100).getBytes(UTF_8);

      assertThat(chain.decode(chain.encode(input))).isEqualTo(input);
    }
  }
}
