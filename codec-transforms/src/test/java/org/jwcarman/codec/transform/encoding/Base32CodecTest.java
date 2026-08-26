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
import org.junit.jupiter.params.provider.ValueSource;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.transform.compress.GzipCodec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class Base32CodecTest {

  @Nested
  class Rfc_4648_base32_vectors {

    @ParameterizedTest(name = "BASE32(\"{0}\") = \"{1}\"")
    @CsvSource({
      "'', ''",
      "f, MY======",
      "fo, MZXQ====",
      "foo, MZXW6===",
      "foob, MZXW6YQ=",
      "fooba, MZXW6YTB",
      "foobar, MZXW6YTBOI======"
    })
    void encodes_the_published_vectors(String input, String expected) {
      byte[] encoded = Base32Codec.standard().encode(input.getBytes(UTF_8));

      assertThat(new String(encoded, US_ASCII)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{1}\" decodes to \"{0}\"")
    @CsvSource({
      "'', ''",
      "f, MY======",
      "fo, MZXQ====",
      "foo, MZXW6===",
      "foob, MZXW6YQ=",
      "fooba, MZXW6YTB",
      "foobar, MZXW6YTBOI======"
    })
    void decodes_the_published_vectors(String expected, String encoded) {
      byte[] decoded = Base32Codec.standard().decode(encoded.getBytes(US_ASCII));

      assertThat(new String(decoded, UTF_8)).isEqualTo(expected);
    }
  }

  @Nested
  class Rfc_4648_base32hex_vectors {

    @ParameterizedTest(name = "BASE32-HEX(\"{0}\") = \"{1}\"")
    @CsvSource({
      "'', ''",
      "f, CO======",
      "fo, CPNG====",
      "foo, CPNMU===",
      "foob, CPNMUOG=",
      "fooba, CPNMUOJ1",
      "foobar, CPNMUOJ1E8======"
    })
    void encodes_the_published_vectors(String input, String expected) {
      byte[] encoded = Base32Codec.hex().encode(input.getBytes(UTF_8));

      assertThat(new String(encoded, US_ASCII)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{1}\" decodes to \"{0}\"")
    @CsvSource({"foo, CPNMU===", "foobar, CPNMUOJ1E8======"})
    void decodes_the_published_vectors(String expected, String encoded) {
      byte[] decoded = Base32Codec.hex().decode(encoded.getBytes(US_ASCII));

      assertThat(new String(decoded, UTF_8)).isEqualTo(expected);
    }

    @Test
    void preserves_byte_order_when_sorted() {
      String low = new String(Base32Codec.hex().encode(new byte[] {0x00, 0x01}), US_ASCII);
      String high = new String(Base32Codec.hex().encode(new byte[] {0x00, 0x02}), US_ASCII);

      assertThat(low).isLessThan(high);
    }
  }

  @Nested
  class Strict_decoding {

    @Test
    void accepts_lower_case_input() {
      byte[] decoded = Base32Codec.standard().decode("mzxw6ytboi======".getBytes(US_ASCII));

      assertThat(new String(decoded, UTF_8)).isEqualTo("foobar");
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"MZXW6YT", "MZXW6YTBO", "MZXW6YTBOI====="})
    void rejects_a_length_that_is_not_a_multiple_of_eight(String bad) {
      byte[] bytes = bad.getBytes(US_ASCII);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> Base32Codec.standard().decode(bytes))
          .withMessageContaining("multiple of 8");
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"MZX=====", "MZXW6Y==", "M======="})
    void rejects_padding_lengths_the_rfc_never_produces(String bad) {
      byte[] bytes = bad.getBytes(US_ASCII);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> Base32Codec.standard().decode(bytes))
          .withMessageContaining("padding");
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"MZXW6Y=B", "MZXW6YT1", "MZXW6YT!", "0ZXW6YTB"})
    void rejects_characters_outside_the_alphabet(String bad) {
      byte[] bytes = bad.getBytes(US_ASCII);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> Base32Codec.standard().decode(bytes))
          .withMessageContaining("character");
    }

    @Test
    void rejects_non_ascii_bytes() {
      byte[] bytes = {(byte) 0xC3, (byte) 0xA9, 'A', 'A', 'A', 'A', 'A', 'A'};

      assertThatIllegalArgumentException().isThrownBy(() -> Base32Codec.standard().decode(bytes));
    }

    @Test
    void the_hex_alphabet_rejects_standard_only_letters() {
      byte[] bytes = "MZXW6YTB".getBytes(US_ASCII);

      assertThatIllegalArgumentException().isThrownBy(() -> Base32Codec.hex().decode(bytes));
    }

    @Test
    void rejects_null_input() {
      assertThatNullPointerException().isThrownBy(() -> Base32Codec.standard().decode(null));
      assertThatNullPointerException().isThrownBy(() -> Base32Codec.standard().encode(null));
    }
  }

  @Nested
  class Round_tripping {

    @Test
    void round_trips_every_length_up_to_a_full_group_boundary() {
      Base32Codec codec = Base32Codec.standard();
      for (int length = 0; length <= 41; length++) {
        byte[] input = new byte[length];
        for (int i = 0; i < length; i++) {
          input[i] = (byte) (i * 37 + length);
        }

        assertThat(codec.decode(codec.encode(input))).isEqualTo(input);
      }
    }

    @Test
    void round_trips_every_byte_value() {
      byte[] all = new byte[256];
      for (int i = 0; i < 256; i++) {
        all[i] = (byte) i;
      }

      assertThat(Base32Codec.hex().decode(Base32Codec.hex().encode(all))).isEqualTo(all);
    }

    @Test
    void composes_as_the_last_transform_in_a_chain() {
      Codec<byte[]> chain = new GzipCodec().andThen(Base32Codec.standard());
      byte[] input = "abc".repeat(200).getBytes(UTF_8);

      byte[] encoded = chain.encode(input);

      assertThat(new String(encoded, US_ASCII)).matches("[A-Z2-7]+=*");
      assertThat(chain.decode(encoded)).isEqualTo(input);
    }
  }
}
