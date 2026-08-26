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
package org.jwcarman.codec.transform.checksum;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.transform.encoding.HexCodec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChecksumCodecTest {

  private static final byte[] CHECK = "123456789".getBytes(UTF_8);

  private final ChecksumCodec codec = ChecksumCodec.crc32c();

  @Nested
  class Known_answers {

    @Test
    void crc32c_of_the_standard_check_string_is_e3069283() {
      byte[] encoded = codec.encode(CHECK);

      assertThat(encoded)
          .hasSize(CHECK.length + 4)
          .startsWith(CHECK)
          .endsWith((byte) 0xE3, (byte) 0x06, (byte) 0x92, (byte) 0x83);
    }

    @Test
    void crc32_of_the_standard_check_string_is_cbf43926() {
      byte[] encoded = new ChecksumCodec(CRC32::new).encode(CHECK);

      assertThat(encoded).endsWith((byte) 0xCB, (byte) 0xF4, (byte) 0x39, (byte) 0x26);
    }

    @Test
    void adler32_of_the_standard_check_string_is_091e01de() {
      byte[] encoded = new ChecksumCodec(Adler32::new).encode(CHECK);

      assertThat(encoded).endsWith((byte) 0x09, (byte) 0x1E, (byte) 0x01, (byte) 0xDE);
    }
  }

  @Nested
  class Round_tripping {

    @Test
    void returns_the_payload_when_the_checksum_matches() {
      assertThat(codec.decode(codec.encode(CHECK))).isEqualTo(CHECK);
    }

    @Test
    void round_trips_an_empty_payload() {
      byte[] encoded = codec.encode(new byte[0]);

      assertThat(encoded).hasSize(4);
      assertThat(codec.decode(encoded)).isEmpty();
    }

    @Test
    void composes_with_another_transform() {
      Codec<byte[]> chain = codec.andThen(HexCodec.lowerCase());

      assertThat(chain.decode(chain.encode(CHECK))).isEqualTo(CHECK);
    }
  }

  @Nested
  class Corruption {

    @Test
    void detects_a_flipped_payload_bit() {
      byte[] encoded = codec.encode(CHECK);
      encoded[3] ^= 0x01;

      assertThatIllegalArgumentException()
          .isThrownBy(() -> codec.decode(encoded))
          .withMessageContaining("corrupt");
    }

    @Test
    void detects_a_flipped_checksum_bit() {
      byte[] encoded = codec.encode(CHECK);
      encoded[encoded.length - 1] ^= (byte) 0x80;

      assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(encoded));
    }

    @Test
    void detects_truncation() {
      byte[] encoded = codec.encode(CHECK);
      byte[] truncated = java.util.Arrays.copyOf(encoded, encoded.length - 1);

      assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(truncated));
    }

    @Test
    void rejects_input_shorter_than_a_checksum() {
      byte[] tooShort = {1, 2, 3};

      assertThatIllegalArgumentException()
          .isThrownBy(() -> codec.decode(tooShort))
          .withMessageContaining("shorter");
    }

    @Test
    void a_different_checksum_does_not_verify_the_same_trailer() {
      byte[] encoded = codec.encode(CHECK);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> new ChecksumCodec(CRC32::new).decode(encoded));
    }
  }

  @Nested
  class Validation {

    @Test
    void rejects_null_input() {
      assertThatNullPointerException().isThrownBy(() -> codec.encode(null));
      assertThatNullPointerException().isThrownBy(() -> codec.decode(null));
    }

    @Test
    void rejects_a_checksum_wider_than_32_bits_instead_of_truncating() {
      Checksum sixtyFourBit =
          new Checksum() {
            @Override
            public void update(int b) {
              // The value is fixed; only its width matters to this test.
            }

            @Override
            public void update(byte[] b, int off, int len) {
              // The value is fixed; only its width matters to this test.
            }

            @Override
            public long getValue() {
              return 1L << 40;
            }

            @Override
            public void reset() {
              // Nothing to reset: the value is fixed.
            }
          };

      assertThatIllegalStateException()
          .isThrownBy(() -> new ChecksumCodec(() -> sixtyFourBit).encode(CHECK))
          .withMessageContaining("wider than 32 bits");
    }

    @Test
    void rejects_a_null_supplier() {
      assertThatNullPointerException().isThrownBy(() -> new ChecksumCodec(null));
    }
  }
}
