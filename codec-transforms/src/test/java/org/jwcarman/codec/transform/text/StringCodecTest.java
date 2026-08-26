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
package org.jwcarman.codec.transform.text;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.transform.encoding.Base64Codec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StringCodecTest {

  @Nested
  class Utf8 {

    @Test
    void encodes_to_the_raw_utf8_bytes_without_quoting() {
      assertThat(StringCodec.utf8().encode("héllo")).isEqualTo("héllo".getBytes(UTF_8));
    }

    @Test
    void round_trips_text_outside_the_basic_multilingual_plane() {
      String text = "snow \u2603 and \uD83D\uDE00";

      assertThat(StringCodec.utf8().decode(StringCodec.utf8().encode(text))).isEqualTo(text);
    }

    @Test
    void round_trips_the_empty_string() {
      assertThat(StringCodec.utf8().decode(StringCodec.utf8().encode(""))).isEmpty();
    }

    @Test
    void an_unpaired_surrogate_encodes_as_the_replacement_character() {
      byte[] encoded = StringCodec.utf8().encode("a\uD800b");

      assertThat(new String(encoded, UTF_8)).isEqualTo("a?b");
    }
  }

  @Nested
  class Other_charsets {

    @Test
    void encodes_with_the_given_charset() {
      assertThat(StringCodec.of(ISO_8859_1).encode("é")).isEqualTo(new byte[] {(byte) 0xE9});
    }

    @Test
    void decodes_with_the_given_charset() {
      assertThat(StringCodec.of(ISO_8859_1).decode(new byte[] {(byte) 0xE9})).isEqualTo("é");
    }

    @Test
    void rejects_a_null_charset() {
      assertThatNullPointerException().isThrownBy(() -> StringCodec.of(null));
    }
  }

  @Nested
  class Strict_decoding {

    @Test
    void rejects_malformed_utf8_instead_of_substituting() {
      byte[] truncatedSequence = {(byte) 'a', (byte) 0xC3};

      assertThatIllegalArgumentException()
          .isThrownBy(() -> StringCodec.utf8().decode(truncatedSequence))
          .withMessageContaining("UTF-8");
    }

    @Test
    void rejects_bytes_unmappable_in_the_charset() {
      byte[] undefinedInAscii = {(byte) 0x80};

      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  StringCodec.of(java.nio.charset.StandardCharsets.US_ASCII)
                      .decode(undefinedInAscii));
    }

    @Test
    void rejects_null_input() {
      assertThatNullPointerException().isThrownBy(() -> StringCodec.utf8().decode(null));
      assertThatNullPointerException().isThrownBy(() -> StringCodec.utf8().encode(null));
    }
  }

  @Nested
  class Composition {

    @Test
    void serves_as_a_backend_free_base_for_xmap() {
      Codec<UUID> ids = StringCodec.utf8().xmap(UUID::fromString, UUID::toString);
      UUID id = UUID.randomUUID();

      assertThat(ids.decode(ids.encode(id))).isEqualTo(id);
    }

    @Test
    void composes_with_a_byte_transform() {
      Codec<String> chain = StringCodec.utf8().andThen(Base64Codec.basic());

      assertThat(new String(chain.encode("hello"), UTF_8)).isEqualTo("aGVsbG8=");
      assertThat(chain.decode("aGVsbG8=".getBytes(UTF_8))).isEqualTo("hello");
    }
  }
}
