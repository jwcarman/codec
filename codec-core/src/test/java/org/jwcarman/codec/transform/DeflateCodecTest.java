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
package org.jwcarman.codec.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DeflateCodecTest {

  private final DeflateCodec codec = new DeflateCodec();

  @Nested
  class Encode {

    @Test
    void produces_zlib_formatted_output() {
      byte[] encoded = codec.encode("hello".getBytes(StandardCharsets.UTF_8));

      assertThat(encoded[0]).isEqualTo((byte) 0x78);
    }

    @Test
    void compresses_repetitive_input() {
      byte[] input = "abc".repeat(1000).getBytes(StandardCharsets.UTF_8);

      byte[] encoded = codec.encode(input);

      assertThat(encoded).hasSizeLessThan(input.length);
    }

    @Test
    void honors_the_compression_level() {
      byte[] input = "abc".repeat(1000).getBytes(StandardCharsets.UTF_8);
      byte[] best = new DeflateCodec(Deflater.BEST_COMPRESSION, Long.MAX_VALUE).encode(input);
      byte[] none = new DeflateCodec(Deflater.NO_COMPRESSION, Long.MAX_VALUE).encode(input);

      assertThat(best.length).isLessThan(none.length);
    }

    @Test
    void wraps_io_failures_in_unchecked_io_exception() {
      try (var _ =
          mockConstruction(
              DeflaterOutputStream.class,
              (mock, context) ->
                  doThrow(new IOException("boom")).when(mock).write(any(byte[].class)))) {
        assertThatExceptionOfType(UncheckedIOException.class)
            .isThrownBy(() -> codec.encode(new byte[] {1, 2, 3}));
      }
    }
  }

  @Nested
  class Decode {

    @Test
    void round_trips_arbitrary_bytes() {
      byte[] input = "the quick brown fox".getBytes(StandardCharsets.UTF_8);

      assertThat(codec.decode(codec.encode(input))).isEqualTo(input);
    }

    @Test
    void round_trips_empty_input() {
      byte[] input = new byte[0];

      assertThat(codec.decode(codec.encode(input))).isEqualTo(input);
    }

    @Test
    void rejects_non_deflate_input() {
      byte[] garbage = {1, 2, 3, 4};

      assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> codec.decode(garbage));
    }
  }

  @Nested
  class Decoded_size_cap {

    @Test
    void rejects_payloads_expanding_beyond_the_cap() {
      byte[] bomb = codec.encode(new byte[100_000]);
      DeflateCodec capped = new DeflateCodec(16);

      assertThatExceptionOfType(IllegalStateException.class)
          .isThrownBy(() -> capped.decode(bomb))
          .withMessageContaining("16");
    }

    @Test
    void allows_payloads_exactly_at_the_cap() {
      byte[] input = "nineteen bytes long".getBytes(StandardCharsets.UTF_8);
      DeflateCodec capped = new DeflateCodec(input.length);

      assertThat(capped.decode(capped.encode(input))).isEqualTo(input);
    }

    @Test
    void rejects_a_non_positive_cap() {
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> new DeflateCodec(0));
    }
  }

  @Nested
  class Compression_level_validation {

    @Test
    void rejects_a_level_above_nine() {
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> new DeflateCodec(10, 1024));
    }

    @Test
    void rejects_a_level_below_default_sentinel() {
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> new DeflateCodec(-2, 1024));
    }
  }
}
