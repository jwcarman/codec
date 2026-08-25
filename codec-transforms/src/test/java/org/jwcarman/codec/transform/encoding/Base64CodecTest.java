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
package org.jwcarman.codec.transform.encoding;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.transform.compress.GzipCodec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class Base64CodecTest {

  private static final byte[] BINARY = {
    (byte) 0xFB, (byte) 0xFF, (byte) 0xBF, 0x00, 0x7F, (byte) 0x80, 0x3E, 0x3F
  };

  @Nested
  class Encode {

    @Test
    void basic_output_is_standard_alphabet_with_padding() {
      byte[] encoded = Base64Codec.basic().encode("hello".getBytes(UTF_8));

      assertThat(new String(encoded, US_ASCII)).isEqualTo("aGVsbG8=");
    }

    @Test
    void url_safe_output_never_contains_plus_or_slash() {
      byte[] encoded = Base64Codec.urlSafe().encode(BINARY);

      assertThat(new String(encoded, US_ASCII)).doesNotContain("+").doesNotContain("/");
    }

    @Test
    void url_safe_without_padding_omits_the_trailing_equals() {
      byte[] encoded = Base64Codec.urlSafeWithoutPadding().encode("hello".getBytes(UTF_8));

      assertThat(new String(encoded, US_ASCII)).isEqualTo("aGVsbG8");
    }

    @Test
    void mime_output_wraps_lines_at_76_characters() {
      byte[] encoded = Base64Codec.mime().encode(new byte[120]);

      assertThat(new String(encoded, US_ASCII)).contains("\r\n");
    }

    @Test
    void output_is_pure_ascii() {
      byte[] encoded = Base64Codec.basic().encode(BINARY);

      for (byte b : encoded) {
        assertThat(b).isBetween((byte) 0x20, (byte) 0x7E);
      }
    }
  }

  @Nested
  class Decode {

    @Test
    void every_variant_round_trips_arbitrary_bytes() {
      List<Base64Codec> variants =
          List.of(
              Base64Codec.basic(),
              Base64Codec.urlSafe(),
              Base64Codec.urlSafeWithoutPadding(),
              Base64Codec.mime());

      for (Base64Codec codec : variants) {
        assertThat(codec.decode(codec.encode(BINARY))).isEqualTo(BINARY);
      }
    }

    @Test
    void round_trips_empty_input() {
      Base64Codec codec = Base64Codec.basic();

      assertThat(codec.decode(codec.encode(new byte[0]))).isEmpty();
    }

    @Test
    void rejects_input_outside_the_alphabet() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> Base64Codec.basic().decode("not*base64!".getBytes(US_ASCII)));
    }

    @Test
    void rejects_null_input() {
      assertThatNullPointerException().isThrownBy(() -> Base64Codec.basic().decode(null));
    }
  }

  @Nested
  class Composed_with_and_then {

    @Test
    void text_safe_output_wraps_a_compressed_payload() {
      Codec<byte[]> chain = new GzipCodec().andThen(Base64Codec.urlSafeWithoutPadding());
      byte[] input = "abc".repeat(500).getBytes(UTF_8);

      byte[] encoded = chain.encode(input);

      assertThat(new String(encoded, US_ASCII)).matches("[A-Za-z0-9_-]+");
      assertThat(chain.decode(encoded)).isEqualTo(input);
    }
  }
}
