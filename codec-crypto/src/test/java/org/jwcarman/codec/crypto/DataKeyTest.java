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
package org.jwcarman.codec.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DataKeyTest {

  private static final SecretKey KEY = new SecretKeySpec(new byte[32], "AES");

  @Nested
  class Validation {
    @Test
    void rejects_null_key_id() {
      assertThatNullPointerException().isThrownBy(() -> new DataKey(null, KEY, new byte[] {1}));
    }

    @Test
    void rejects_empty_key_id() {
      assertThatIllegalArgumentException().isThrownBy(() -> new DataKey("", KEY, new byte[] {1}));
    }

    @Test
    void rejects_key_id_longer_than_uint16_in_utf8_bytes() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new DataKey("x".repeat(65536), KEY, new byte[] {1}));
    }

    @Test
    void rejects_empty_wrapped() {
      assertThatIllegalArgumentException().isThrownBy(() -> new DataKey("kek", KEY, new byte[0]));
    }

    @Test
    void rejects_wrapped_longer_than_uint16() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new DataKey("kek", KEY, new byte[65536]));
    }

    @Test
    void key_id_length_is_measured_in_utf8_bytes_not_chars() {
      String multibyte = "é".repeat(40000); // 80000 UTF-8 bytes > 65535
      assertThat(multibyte.length()).isLessThan(65536);
      assertThat(multibyte.getBytes(UTF_8).length).isGreaterThan(65535);
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new DataKey(multibyte, KEY, new byte[] {1}));
    }

    @Test
    void accepts_a_one_byte_key_id_at_the_lower_bound() {
      assertThat(new DataKey("k", KEY, new byte[] {1}).keyId()).isEqualTo("k");
    }

    @Test
    void accepts_a_key_id_at_exactly_the_uint16_upper_bound() {
      String maxKeyId = "k".repeat(65535);
      assertThat(new DataKey(maxKeyId, KEY, new byte[] {1}).keyId()).isEqualTo(maxKeyId);
    }

    @Test
    void accepts_wrapped_bytes_at_exactly_the_uint16_upper_bound() {
      byte[] maxWrapped = new byte[65535];
      assertThat(new DataKey("kek", KEY, maxWrapped).wrapped()).hasSize(65535);
    }
  }

  @Nested
  class Defensive_copies {
    @Test
    void mutating_the_constructor_argument_does_not_affect_the_record() {
      byte[] wrapped = {1, 2, 3};
      DataKey dk = new DataKey("kek", KEY, wrapped);
      wrapped[0] = 99;
      assertThat(dk.wrapped()).containsExactly(1, 2, 3);
    }

    @Test
    void mutating_the_accessor_result_does_not_affect_the_record() {
      DataKey dk = new DataKey("kek", KEY, new byte[] {1, 2, 3});
      dk.wrapped()[0] = 99;
      assertThat(dk.wrapped()).containsExactly(1, 2, 3);
    }
  }

  @Nested
  class Equality_and_printing {
    @Test
    void equal_on_key_id_and_wrapped_content_ignoring_key() {
      SecretKey other =
          new SecretKeySpec(
              new byte[] {
                9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
                9, 9, 9, 9
              },
              "AES");
      assertThat(new DataKey("kek", KEY, new byte[] {1, 2}))
          .isEqualTo(new DataKey("kek", other, new byte[] {1, 2}))
          .hasSameHashCodeAs(new DataKey("kek", other, new byte[] {1, 2}));
      assertThat(new DataKey("kek", KEY, new byte[] {1, 2}))
          .isNotEqualTo(new DataKey("kek", KEY, new byte[] {1, 3}));
    }

    @Test
    void is_not_equal_to_an_instance_of_an_unrelated_type() {
      DataKey dk = new DataKey("kek", KEY, new byte[] {1, 2});
      assertThat(dk).isNotEqualTo("kek");
    }

    @Test
    void is_not_equal_to_null() {
      DataKey dk = new DataKey("kek", KEY, new byte[] {1, 2});
      assertThat(dk.equals(null)).isFalse();
    }

    @Test
    void hash_code_combines_key_id_and_wrapped_via_the_documented_formula() {
      byte[] wrapped = {1, 2, 3};
      DataKey dk = new DataKey("kek", KEY, wrapped);
      int expected = 31 * "kek".hashCode() + java.util.Arrays.hashCode(wrapped);
      assertThat(dk.hashCode()).isEqualTo(expected);
    }

    @Test
    void to_string_reveals_key_id_and_wrapped_length_but_no_key_material() {
      DataKey dk =
          new DataKey(
              "kek",
              new SecretKeySpec("supersecretkey--supersecretkey--".getBytes(UTF_8), "AES"),
              new byte[] {1, 2, 3});
      assertThat(dk.toString()).contains("kek").contains("3").doesNotContain("supersecret");
    }
  }
}
