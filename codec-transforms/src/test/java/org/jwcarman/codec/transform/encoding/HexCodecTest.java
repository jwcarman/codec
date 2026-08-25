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

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.transform.compress.GzipCodec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HexCodecTest {

  @Nested
  class Rfc_4648_base16_vectors {

    @ParameterizedTest(name = "BASE16(\"{0}\") = \"{1}\"")
    @CsvSource({
      "'', ''",
      "f, 66",
      "fo, 666F",
      "foo, 666F6F",
      "foob, 666F6F62",
      "fooba, 666F6F6261",
      "foobar, 666F6F626172"
    })
    void encodes_the_published_vectors_in_upper_case(String input, String expected) {
      byte[] encoded = HexCodec.upperCase().encode(input.getBytes(UTF_8));

      assertThat(new String(encoded, US_ASCII)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{1}\" decodes to \"{0}\"")
    @CsvSource({"f, 66", "foo, 666F6F", "foobar, 666F6F626172"})
    void decodes_the_published_vectors(String expected, String hex) {
      byte[] decoded = HexCodec.upperCase().decode(hex.getBytes(US_ASCII));

      assertThat(new String(decoded, UTF_8)).isEqualTo(expected);
    }
  }

  @Nested
  class Case_handling {

    @Test
    void lower_case_is_the_default_form() {
      byte[] encoded = HexCodec.lowerCase().encode(new byte[] {(byte) 0xAB, (byte) 0xCD});

      assertThat(new String(encoded, US_ASCII)).isEqualTo("abcd");
    }

    @Test
    void decoding_accepts_either_case() {
      byte[] expected = {(byte) 0xAB, (byte) 0xCD};

      assertThat(HexCodec.lowerCase().decode("ABCD".getBytes(US_ASCII))).isEqualTo(expected);
      assertThat(HexCodec.upperCase().decode("abcd".getBytes(US_ASCII))).isEqualTo(expected);
    }
  }

  @Nested
  class Strict_decoding {

    @Test
    void rejects_odd_length_input() {
      byte[] odd = "abc".getBytes(US_ASCII);

      assertThatIllegalArgumentException().isThrownBy(() -> HexCodec.lowerCase().decode(odd));
    }

    @Test
    void rejects_non_hex_characters() {
      byte[] bad = "zz".getBytes(US_ASCII);

      assertThatIllegalArgumentException().isThrownBy(() -> HexCodec.lowerCase().decode(bad));
    }

    @Test
    void rejects_null_input() {
      assertThatNullPointerException().isThrownBy(() -> HexCodec.lowerCase().decode(null));
    }
  }

  @Nested
  class Round_tripping {

    @Test
    void round_trips_every_byte_value() {
      byte[] all = new byte[256];
      for (int i = 0; i < 256; i++) {
        all[i] = (byte) i;
      }
      HexCodec codec = HexCodec.lowerCase();

      assertThat(codec.decode(codec.encode(all))).isEqualTo(all);
    }

    @Test
    void composes_as_the_last_transform_in_a_chain() {
      Codec<byte[]> chain = new GzipCodec().andThen(HexCodec.lowerCase());
      byte[] input = "abc".repeat(200).getBytes(UTF_8);

      byte[] encoded = chain.encode(input);

      assertThat(new String(encoded, US_ASCII)).matches("[0-9a-f]+");
      assertThat(chain.decode(encoded)).isEqualTo(input);
    }
  }
}
