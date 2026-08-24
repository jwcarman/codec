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
package org.jwcarman.codec.jackson2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.TypeRef;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class Jackson2CodecFactoryTest {

  private Jackson2CodecFactory factory;

  @BeforeEach
  void setUp() {
    factory = new Jackson2CodecFactory(new ObjectMapper());
  }

  record Person(String name, int age, boolean active) {}

  @Nested
  class Round_tripping {

    @Test
    void handles_a_simple_pojo() {
      Codec<Person> codec = factory.create(Person.class);
      Person original = new Person("Alice", 30, true);

      assertThat(codec.decode(codec.encode(original))).isEqualTo(original);
    }

    @Test
    void handles_null_fields() {
      Codec<Person> codec = factory.create(Person.class);
      Person original = new Person(null, 0, false);

      assertThat(codec.decode(codec.encode(original))).isEqualTo(original);
    }

    @Test
    void handles_a_generic_list_via_type_ref() {
      Codec<List<Person>> codec = factory.create(new TypeRef<List<Person>>() {});
      List<Person> original = List.of(new Person("Alice", 30, true), new Person("Bob", 25, false));

      assertThat(codec.decode(codec.encode(original))).isEqualTo(original);
    }

    @Test
    void handles_a_generic_map_via_type_ref() {
      Codec<Map<String, Integer>> codec = factory.create(new TypeRef<Map<String, Integer>>() {});
      Map<String, Integer> original = Map.of("a", 1, "b", 2);

      assertThat(codec.decode(codec.encode(original))).isEqualTo(original);
    }

    @Test
    void handles_empty_collections() {
      Codec<List<String>> codec = factory.create(new TypeRef<List<String>>() {});

      assertThat(codec.decode(codec.encode(List.of()))).isEmpty();
    }
  }

  @Nested
  class Encoding_unsupported_values {

    @Test
    void wraps_jackson_failures_in_unchecked_io_exception() {
      Codec<Object> codec = factory.create(Object.class);
      Object unserializable = new Object();

      assertThatExceptionOfType(UncheckedIOException.class)
          .isThrownBy(() -> codec.encode(unserializable));
    }
  }

  @Nested
  class Decoding_invalid_input {

    @Test
    void wraps_jackson_failures_in_unchecked_io_exception() {
      Codec<Person> codec = factory.create(Person.class);
      byte[] garbage = "not json".getBytes(StandardCharsets.UTF_8);

      assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> codec.decode(garbage));
    }
  }
}
