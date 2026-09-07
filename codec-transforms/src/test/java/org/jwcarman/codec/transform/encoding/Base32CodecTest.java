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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Random;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;
import org.jwcarman.codec.transform.compress.GzipCodec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class Base32CodecTest {

  static byte[] ascii(String s) {
    return s.getBytes(US_ASCII);
  }

  @Nested
  class Construction {

    @Test
    void reports_the_alphabet() {
      assertThat(Base32Codec.standard().alphabet()).isEqualTo("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567");
      assertThat(Base32Codec.hex().alphabet()).isEqualTo("0123456789ABCDEFGHIJKLMNOPQRSTUV");
      assertThat(Base32Codec.of("ybndrfg8ejkmcpqxot1uwisza345h769").alphabet())
          .isEqualTo("ybndrfg8ejkmcpqxot1uwisza345h769");
    }

    @ParameterizedTest(name = "rejects an alphabet of {0} symbols")
    @ValueSource(ints = {0, 31, 33})
    void rejects_an_alphabet_that_is_not_thirty_two_symbols(int length) {
      String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567abcdefgh".substring(0, length);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> Base32Codec.of(alphabet))
          .withMessageContaining("32")
          .withMessageContaining(String.valueOf(length));
    }

    @Test
    void rejects_a_null_alphabet() {
      assertThatNullPointerException().isThrownBy(() -> Base32Codec.of(null));
    }

    @Test
    void rejects_a_non_ascii_symbol() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> Base32Codec.of("ABCDEFGHIJKLMNOPQRSTUVWXYZ23456é"))
          .withMessageContaining("ASCII")
          .withMessageContaining("U+00E9");
    }

    @Test
    void rejects_a_repeated_symbol() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> Base32Codec.of("ABCDEFGHIJKLMNOPQRSTUVWXYZ23456A"))
          .withMessageContaining("twice")
          .withMessageContaining("'A'");
    }

    @Test
    void rejects_a_pad_symbol_that_is_in_the_alphabet() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> Base32Codec.of("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", 'A'))
          .withMessageContaining("pad")
          .withMessageContaining("'A'");
    }

    @Test
    void rejects_a_non_ascii_pad_symbol() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> Base32Codec.of("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", 'é'))
          .withMessageContaining("pad")
          .withMessageContaining("ASCII");
    }
  }

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
    void rejects_lower_case_input_by_default() {
      byte[] bytes = ascii("mzxw6ytboi======");
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("character")
          .withMessageContaining("'m'");
    }

    @Test
    void rejects_non_zero_trailing_bits() {
      byte[] bytes = ascii("MZ======");
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("trailing bits");
      assertThat(codec.decode(ascii("MY======"))).isEqualTo("f".getBytes(UTF_8));
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"MZ=W6YTB", "MZXW6Y=B"})
    void rejects_a_pad_symbol_before_the_end(String bad) {
      byte[] bytes = ascii(bad);
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("padding")
          .withMessageContaining("'='");
    }

    @Test
    void rejects_a_whole_group_of_padding() {
      byte[] bytes = ascii("MZXW6YTB========");
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("padding");
    }

    @Test
    void rejects_a_bad_symbol_inside_a_whole_group() {
      byte[] bytes = ascii("MZXW6YTBOI!AAAAA");
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("'!'");
    }

    @Test
    void names_a_non_ascii_byte_by_its_code_point() {
      byte[] bytes = {(byte) 0xC3, 'Z', 'X', 'W', '6', 'Y', 'T', 'B'};
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("U+00C3");
    }

    @Test
    void names_a_control_character_by_its_code_point() {
      byte[] bytes = {(byte) 0x01, 'Z', 'X', 'W', '6', 'Y', 'T', 'B'};
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("U+0001");
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"MZXW6YT", "MZXW6YTBO", "MZXW6YTBOI====="})
    void rejects_a_length_that_is_not_a_multiple_of_eight(String bad) {
      byte[] bytes = bad.getBytes(US_ASCII);
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("multiple of 8");
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"MZX=====", "MZXW6Y==", "M======="})
    void rejects_padding_lengths_the_rfc_never_produces(String bad) {
      byte[] bytes = bad.getBytes(US_ASCII);
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("padding");
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"MZXW6YT1", "MZXW6YT!", "0ZXW6YTB"})
    void rejects_characters_outside_the_alphabet(String bad) {
      byte[] bytes = bad.getBytes(US_ASCII);
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("character");
    }

    @Test
    void rejects_non_ascii_bytes() {
      byte[] bytes = {(byte) 0xC3, (byte) 0xA9, 'A', 'A', 'A', 'A', 'A', 'A'};
      Base32Codec codec = Base32Codec.standard();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes));
    }

    @Test
    void the_hex_alphabet_rejects_standard_only_letters() {
      byte[] bytes = "MZXW6YTB".getBytes(US_ASCII);
      Base32Codec codec = Base32Codec.hex();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes));
    }

    @Test
    void rejects_null_input() {
      Base32Codec codec = Base32Codec.standard();
      assertThatNullPointerException().isThrownBy(() -> codec.decode(null));
      assertThatNullPointerException().isThrownBy(() -> codec.encode(null));
    }
  }

  @Nested
  class Without_padding {

    final Base32Codec codec = Base32Codec.of("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567");

    @ParameterizedTest(name = "BASE32(\"{0}\") = \"{1}\"")
    @CsvSource({
      "'', ''",
      "f, MY",
      "fo, MZXQ",
      "foo, MZXW6",
      "foob, MZXW6YQ",
      "fooba, MZXW6YTB",
      "foobar, MZXW6YTBOI"
    })
    void encodes_without_padding_and_decodes_it_back(String input, String expected) {
      byte[] encoded = codec.encode(input.getBytes(UTF_8));

      assertThat(new String(encoded, US_ASCII)).isEqualTo(expected);
      assertThat(codec.decode(encoded)).isEqualTo(input.getBytes(UTF_8));
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"M", "MZX", "MZXW6Y", "MZXW6YTBO"})
    void rejects_a_tail_that_does_not_encode_whole_bytes(String bad) {
      byte[] bytes = ascii(bad);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("length");
    }

    @Test
    void treats_the_rfc_pad_symbol_as_any_other_bad_character() {
      byte[] bytes = ascii("MY======");

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("character")
          .withMessageContaining("'='");
    }

    @Test
    void rejects_non_zero_trailing_bits() {
      byte[] bytes = ascii("MZ");

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withMessageContaining("trailing bits");
    }

    @Test
    void round_trips_every_length_up_to_a_full_group_boundary() {
      for (int length = 0; length <= 41; length++) {
        byte[] input = new byte[length];
        for (int i = 0; i < length; i++) {
          input[i] = (byte) (i * 53 + length);
        }

        assertThat(codec.decode(codec.encode(input))).isEqualTo(input);
      }
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
    void round_trips_a_large_payload() {
      byte[] input = new byte[100_003];
      new Random(100_003).nextBytes(input);
      Base32Codec codec = Base32Codec.standard();

      assertThat(codec.decode(codec.encode(input))).isEqualTo(input);
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
