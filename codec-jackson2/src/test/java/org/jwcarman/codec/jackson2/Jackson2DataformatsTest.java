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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.TypeRef;

/**
 * {@link Jackson2CodecFactory} takes any Jackson 2.x {@link ObjectMapper}, so every Jackson 2
 * dataformat is a backend by mapper swap. These tests prove that claim for the binary and text
 * formats Jackson itself ships.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class Jackson2DataformatsTest {

  record Person(String name, int age, boolean active) {}

  private static final Person ALICE = new Person("Alice", 30, true);

  static Stream<Arguments> mappers() {
    return Stream.of(
        Arguments.of("CBOR", CBORMapper.builder().build()),
        Arguments.of("Smile", SmileMapper.builder().build()),
        Arguments.of("YAML", YAMLMapper.builder().build()),
        Arguments.of("XML", XmlMapper.builder().build()));
  }

  @Nested
  class Every_dataformat {

    @ParameterizedTest(name = "{0} round-trips a record")
    @MethodSource("org.jwcarman.codec.jackson2.Jackson2DataformatsTest#mappers")
    void round_trips_a_record(String format, ObjectMapper mapper) {
      Codec<Person> codec = new Jackson2CodecFactory(mapper).create(Person.class);

      assertThat(codec.decode(codec.encode(ALICE))).isEqualTo(ALICE);
    }

    @ParameterizedTest(name = "{0} round-trips a generic map through TypeRef")
    @MethodSource("org.jwcarman.codec.jackson2.Jackson2DataformatsTest#mappers")
    void round_trips_a_generic_map(String format, ObjectMapper mapper) {
      Codec<Map<String, Person>> codec =
          new Jackson2CodecFactory(mapper).create(new TypeRef<Map<String, Person>>() {});
      Map<String, Person> people = Map.of("alice", ALICE, "bob", new Person("Bob", 41, false));

      assertThat(codec.decode(codec.encode(people))).isEqualTo(people);
    }

    @ParameterizedTest(name = "{0} output is not JSON")
    @MethodSource("org.jwcarman.codec.jackson2.Jackson2DataformatsTest#mappers")
    void output_is_not_json(String format, ObjectMapper mapper) {
      byte[] encoded = new Jackson2CodecFactory(mapper).create(Person.class).encode(ALICE);

      assertThat(encoded[0]).isNotEqualTo((byte) '{');
    }
  }

  @Nested
  class Binary_formats {

    @Test
    void cbor_and_smile_round_trip_a_generic_list() {
      for (ObjectMapper mapper :
          List.of(CBORMapper.builder().build(), SmileMapper.builder().build())) {
        Codec<List<Person>> codec =
            new Jackson2CodecFactory(mapper).create(new TypeRef<List<Person>>() {});
        List<Person> people = List.of(ALICE, new Person("Bob", 41, false));

        assertThat(codec.decode(codec.encode(people))).isEqualTo(people);
      }
    }

    @Test
    void smile_output_carries_its_header() {
      byte[] encoded =
          new Jackson2CodecFactory(SmileMapper.builder().build())
              .create(Person.class)
              .encode(ALICE);

      assertThat(encoded).startsWith((byte) ':', (byte) ')', (byte) '\n');
    }
  }

  @Nested
  class Text_formats {

    @Test
    void yaml_output_is_readable_yaml() {
      byte[] encoded =
          new Jackson2CodecFactory(YAMLMapper.builder().build()).create(Person.class).encode(ALICE);

      assertThat(new String(encoded, UTF_8)).contains("name: \"Alice\"").contains("age: 30");
    }

    @Test
    void xml_output_is_an_element_named_after_the_type() {
      byte[] encoded =
          new Jackson2CodecFactory(XmlMapper.builder().build()).create(Person.class).encode(ALICE);

      assertThat(new String(encoded, UTF_8))
          .startsWith("<Person>")
          .contains("<name>Alice</name>")
          .endsWith("</Person>");
    }
  }
}
