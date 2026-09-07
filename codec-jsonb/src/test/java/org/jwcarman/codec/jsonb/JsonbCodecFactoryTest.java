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
package org.jwcarman.codec.jsonb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import jakarta.json.JsonException;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbProperty;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;
import org.jwcarman.codec.spi.InvalidValueException;
import org.jwcarman.codec.spi.TypeRef;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JsonbCodecFactoryTest {

  public record Person(String name, int age, boolean active) {}

  public record Renamed(@JsonbProperty("full_name") String name) {}

  private static Jsonb jsonb;
  private static JsonbCodecFactory factory;

  @BeforeAll
  static void createJsonb() {
    jsonb = JsonbBuilder.create();
    factory = new JsonbCodecFactory(jsonb);
  }

  @AfterAll
  static void closeJsonb() throws Exception {
    jsonb.close();
  }

  @Nested
  class Round_tripping {

    @Test
    void round_trips_a_record() {
      Codec<Person> codec = factory.create(Person.class);
      Person alice = new Person("Alice", 30, true);

      assertThat(codec.decode(codec.encode(alice))).isEqualTo(alice);
    }

    @Test
    void round_trips_a_record_with_null_fields() {
      Codec<Person> codec = factory.create(Person.class);
      Person nobody = new Person(null, 0, false);

      assertThat(codec.decode(codec.encode(nobody))).isEqualTo(nobody);
    }

    @Test
    void round_trips_a_generic_list_through_type_ref() {
      Codec<List<Person>> codec = factory.create(new TypeRef<List<Person>>() {});
      List<Person> people = List.of(new Person("Alice", 30, true), new Person("Bob", 41, false));

      assertThat(codec.decode(codec.encode(people))).isEqualTo(people);
    }

    @Test
    void round_trips_a_list_built_from_an_element_type_ref() {
      Codec<List<Person>> codec = factory.create(TypeRef.listOf(TypeRef.of(Person.class)));
      List<Person> people = List.of(new Person("Alice", 30, true), new Person("Bob", 41, false));

      assertThat(codec.decode(codec.encode(people))).isEqualTo(people);
    }

    @Test
    void round_trips_a_generic_map_through_type_ref() {
      Codec<Map<String, List<Integer>>> codec =
          factory.create(new TypeRef<Map<String, List<Integer>>>() {});
      Map<String, List<Integer>> scores = Map.of("alice", List.of(1, 2, 3), "bob", List.of());

      assertThat(codec.decode(codec.encode(scores))).isEqualTo(scores);
    }
  }

  @Nested
  class Output {

    @Test
    void encodes_as_utf8_json() {
      byte[] encoded = factory.create(Person.class).encode(new Person("Zoë", 30, true));

      assertThat(new String(encoded, UTF_8))
          .startsWith("{")
          .contains("\"name\":\"Zoë\"")
          .contains("\"age\":30");
    }

    @Test
    void honors_json_b_annotations() {
      byte[] encoded = factory.create(Renamed.class).encode(new Renamed("Alice"));

      assertThat(new String(encoded, UTF_8)).contains("\"full_name\":\"Alice\"");
    }

    @Test
    void honors_the_supplied_configuration() throws Exception {
      Jsonb formatted = JsonbBuilder.create(new JsonbConfig().withFormatting(true));
      try {
        byte[] encoded =
            new JsonbCodecFactory(formatted)
                .create(Person.class)
                .encode(new Person("Alice", 30, true));

        assertThat(new String(encoded, UTF_8)).contains("\n");
      } finally {
        formatted.close();
      }
    }
  }

  @Nested
  class Failures {

    /** A getter that throws: Yasson reports it as a JsonbException. */
    public static class ThrowingGetter {
      public String getBoom() {
        throw new IllegalStateException("boom");
      }
    }

    @Test
    void malformed_json_is_an_invalid_payload() {
      Codec<Person> codec = factory.create(Person.class);
      byte[] notJson = "not json".getBytes(UTF_8);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(notJson))
          .withMessage("Unable to decode JSON")
          .withCauseInstanceOf(JsonbException.class);
    }

    @Test
    void a_value_the_binding_cannot_serialize_is_an_invalid_value() {
      Codec<ThrowingGetter> codec = factory.create(ThrowingGetter.class);
      ThrowingGetter value = new ThrowingGetter();

      assertThatExceptionOfType(InvalidValueException.class)
          .isThrownBy(() -> codec.encode(value))
          .withMessage("Unable to encode value as JSON")
          .withCauseInstanceOf(JsonbException.class);
    }

    /** A Jsonb whose generator and parser fail with JSON-P's own exception, as Johnzon's can. */
    static final class JsonExceptionJsonb implements Jsonb {
      private static final JsonException FAILURE = new JsonException("generator failed");

      @Override
      public <T> T fromJson(String str, Class<T> type) {
        throw FAILURE;
      }

      @Override
      public <T> T fromJson(String str, Type runtimeType) {
        throw FAILURE;
      }

      @Override
      public <T> T fromJson(Reader reader, Class<T> type) {
        throw FAILURE;
      }

      @Override
      public <T> T fromJson(Reader reader, Type runtimeType) {
        throw FAILURE;
      }

      @Override
      public <T> T fromJson(InputStream stream, Class<T> type) {
        throw FAILURE;
      }

      @Override
      public <T> T fromJson(InputStream stream, Type runtimeType) {
        throw FAILURE;
      }

      @Override
      public String toJson(Object object) {
        throw FAILURE;
      }

      @Override
      public String toJson(Object object, Type runtimeType) {
        throw FAILURE;
      }

      @Override
      public void toJson(Object object, Writer writer) {
        throw FAILURE;
      }

      @Override
      public void toJson(Object object, Type runtimeType, Writer writer) {
        throw FAILURE;
      }

      @Override
      public void toJson(Object object, OutputStream stream) {
        throw FAILURE;
      }

      @Override
      public void toJson(Object object, Type runtimeType, OutputStream stream) {
        throw FAILURE;
      }

      @Override
      public void close() {
        // Nothing to release: the fake holds no resources.
      }
    }

    @Test
    void a_json_p_failure_on_encode_is_an_invalid_value() {
      Codec<Person> codec = new JsonbCodecFactory(new JsonExceptionJsonb()).create(Person.class);
      Person person = new Person("Alice", 30, true);

      assertThatExceptionOfType(InvalidValueException.class)
          .isThrownBy(() -> codec.encode(person))
          .withMessage("Unable to encode value as JSON")
          .withCauseInstanceOf(JsonException.class);
    }

    @Test
    void a_json_p_failure_on_decode_is_an_invalid_payload() {
      Codec<Person> codec = new JsonbCodecFactory(new JsonExceptionJsonb()).create(Person.class);
      byte[] json = "{}".getBytes(UTF_8);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(json))
          .withMessage("Unable to decode JSON")
          .withCauseInstanceOf(JsonException.class);
    }

    @Test
    void rejects_a_null_jsonb() {
      assertThatNullPointerException().isThrownBy(() -> new JsonbCodecFactory(null));
    }

    @Test
    void rejects_a_null_type_ref() {
      assertThatNullPointerException().isThrownBy(() -> factory.create((TypeRef<Person>) null));
    }
  }
}
