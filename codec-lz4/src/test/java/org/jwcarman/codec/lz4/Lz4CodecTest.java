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
package org.jwcarman.codec.lz4;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;
import org.jwcarman.codec.transform.compress.GzipCodec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class Lz4CodecTest {

  private final Lz4Codec codec = new Lz4Codec();

  @Nested
  class Encode {

    @Test
    void produces_an_lz4_frame() {
      byte[] encoded = codec.encode("hello".getBytes(UTF_8));

      assertThat(encoded).startsWith((byte) 0x04, (byte) 0x22, (byte) 0x4D, (byte) 0x18);
    }

    @Test
    void compresses_repetitive_input() {
      byte[] input = "abc".repeat(1000).getBytes(UTF_8);

      assertThat(codec.encode(input)).hasSizeLessThan(input.length);
    }

    @Test
    void high_compression_compresses_at_least_as_well() {
      byte[] input = "the quick brown fox jumps over the lazy dog ".repeat(200).getBytes(UTF_8);

      assertThat(Lz4Codec.highCompression().encode(input))
          .hasSizeLessThanOrEqualTo(codec.encode(input).length);
    }

    @Test
    void spans_multiple_blocks_for_large_input() {
      byte[] input = new byte[300_000];
      for (int i = 0; i < input.length; i++) {
        input[i] = (byte) (i * 31);
      }

      assertThat(codec.decode(codec.encode(input))).isEqualTo(input);
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
    void round_trips_across_compressors() {
      byte[] input = "compressor-independent".repeat(50).getBytes(UTF_8);

      assertThat(codec.decode(Lz4Codec.highCompression().encode(input))).isEqualTo(input);
    }

    @Test
    void rejects_non_lz4_input() {
      byte[] notCompressed = "not compressed".getBytes(UTF_8);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(notCompressed));
    }

    @Test
    void rejects_a_corrupted_frame_descriptor() {
      byte[] bad = codec.encode("hello world".getBytes(UTF_8));
      bad[4] ^= 0x40;

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bad))
          .withMessage("Unable to decompress data")
          .withCauseInstanceOf(IOException.class)
          .havingCause()
          .withMessage("Invalid LZ4 frame descriptor");
    }

    @Test
    void rejects_a_corrupted_block_descriptor() {
      byte[] bad = codec.encode("hello world".getBytes(UTF_8));
      bad[5] ^= 0x40;

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bad))
          .withMessage("Unable to decompress data")
          .withCauseInstanceOf(IOException.class)
          .havingCause()
          .withMessage("Invalid LZ4 frame descriptor");
    }

    @Test
    void rejects_a_corrupt_descriptor_in_a_later_frame() {
      byte[] first = codec.encode("hello world".getBytes(UTF_8));
      byte[] secondFrameHeader = {0x04, 0x22, 0x4D, 0x18, 0x41, 0x70, 0x00};
      byte[] concatenated = new byte[first.length + secondFrameHeader.length];
      System.arraycopy(first, 0, concatenated, 0, first.length);
      System.arraycopy(secondFrameHeader, 0, concatenated, first.length, secondFrameHeader.length);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(concatenated))
          .withMessage("Unable to decompress data")
          .withCauseInstanceOf(IOException.class)
          .havingCause()
          .withMessage("Invalid LZ4 frame descriptor");
    }

    @Test
    void reads_concatenated_frames_as_one_payload() {
      byte[] first = codec.encode("hello ".getBytes(UTF_8));
      byte[] second = codec.encode("world".getBytes(UTF_8));
      byte[] concatenated = new byte[first.length + second.length];
      System.arraycopy(first, 0, concatenated, 0, first.length);
      System.arraycopy(second, 0, concatenated, first.length, second.length);

      assertThat(codec.decode(concatenated)).isEqualTo("hello world".getBytes(UTF_8));
    }

    @Test
    void rejects_a_corrupted_frame() {
      byte[] encoded = codec.encode("the quick brown fox jumps".repeat(20).getBytes(UTF_8));
      encoded[encoded.length - 6] ^= 0x55;

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(encoded));
    }
  }

  @Nested
  class Decoded_size_cap {

    @Test
    void rejects_payloads_expanding_beyond_the_cap() {
      byte[] bomb = codec.encode(new byte[10_000]);
      Lz4Codec capped = new Lz4Codec(1_000);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> capped.decode(bomb))
          .withMessageContaining("exceeds the maximum");
    }

    @Test
    void allows_payloads_exactly_at_the_cap() {
      byte[] payload = new byte[1_000];
      byte[] encoded = codec.encode(payload);

      assertThat(new Lz4Codec(1_000).decode(encoded)).isEqualTo(payload);
    }

    @Test
    void rejects_a_non_positive_cap() {
      assertThatIllegalArgumentException().isThrownBy(() -> new Lz4Codec(0));
      assertThatIllegalArgumentException().isThrownBy(() -> Lz4Codec.highCompression(0));
    }
  }

  @Nested
  class Composed_with_and_then {

    @Test
    void round_trips_through_another_transform() {
      Codec<byte[]> chain = new Lz4Codec().andThen(new GzipCodec());
      byte[] input = "layered".repeat(100).getBytes(UTF_8);

      assertThat(chain.decode(chain.encode(input))).isEqualTo(input);
    }
  }

  @Nested
  class The_frame_descriptor_guard {

    @Test
    void single_byte_read_returns_the_first_decompressed_byte() throws IOException {
      byte[] encoded = codec.encode("hello world".getBytes(UTF_8));

      try (InputStream in = codec.decompressing(new ByteArrayInputStream(encoded))) {
        assertThat(in.read()).isEqualTo('h');
      }
    }

    @Test
    void skip_advances_past_decompressed_bytes_for_a_later_single_byte_read() throws IOException {
      byte[] encoded = codec.encode("hello world".getBytes(UTF_8));

      try (InputStream in = codec.decompressing(new ByteArrayInputStream(encoded))) {
        in.skip(6);
        assertThat(in.read()).isEqualTo('w');
      }
    }

    @Test
    void single_byte_read_on_a_corrupt_frame_descriptor_is_an_io_exception() throws IOException {
      byte[] bad = codec.encode("hello world".getBytes(UTF_8));
      bad[4] ^= 0x40;

      try (InputStream in = codec.decompressing(new ByteArrayInputStream(bad))) {
        assertThatExceptionOfType(IOException.class)
            .isThrownBy(in::read)
            .withMessage("Invalid LZ4 frame descriptor")
            .withCauseInstanceOf(RuntimeException.class);
      }
    }

    @Test
    void skip_on_a_corrupt_frame_descriptor_is_an_io_exception() throws IOException {
      byte[] bad = codec.encode("hello world".getBytes(UTF_8));
      bad[4] ^= 0x40;

      try (InputStream in = codec.decompressing(new ByteArrayInputStream(bad))) {
        assertThatExceptionOfType(IOException.class)
            .isThrownBy(() -> in.skip(1))
            .withMessage("Invalid LZ4 frame descriptor")
            .withCauseInstanceOf(RuntimeException.class);
      }
    }
  }
}
