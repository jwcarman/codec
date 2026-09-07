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
package org.jwcarman.codec.fory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.exception.InsecureException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;
import org.jwcarman.codec.spi.InvalidValueException;
import org.jwcarman.codec.spi.TypeRef;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ForyCodecFactoryTest {

  public record Person(String name, int age, boolean active) {}

  public record Order(String id, List<Person> people) {}

  public record Unregistered(String value) {}

  private final ForyCodecFactory factory = ForyCodecFactory.of(Person.class, Order.class);

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
    void round_trips_a_null_value() {
      Codec<Person> codec = factory.create(Person.class);

      assertThat(codec.decode(codec.encode(null))).isNull();
    }

    @Test
    void round_trips_a_generic_list_through_type_ref() {
      Codec<List<Person>> codec = factory.create(new TypeRef<List<Person>>() {});
      List<Person> people = List.of(new Person("Alice", 30, true), new Person("Bob", 41, false));

      assertThat(codec.decode(codec.encode(people))).containsExactlyElementsOf(people);
    }

    @Test
    void round_trips_a_generic_map_through_type_ref() {
      Codec<Map<String, Person>> codec = factory.create(new TypeRef<Map<String, Person>>() {});
      Map<String, Person> people = Map.of("alice", new Person("Alice", 30, true));

      assertThat(codec.decode(codec.encode(people))).containsExactlyEntriesOf(people);
    }

    @Test
    void round_trips_nested_registered_types() {
      Codec<Order> codec = factory.create(Order.class);
      Order order = new Order("o-1", List.of(new Person("Alice", 30, true)));

      assertThat(codec.decode(codec.encode(order))).isEqualTo(order);
    }
  }

  @Nested
  class Output {

    @Test
    void output_is_binary_not_json() {
      byte[] encoded = factory.create(Person.class).encode(new Person("Alice", 30, true));

      assertThat(encoded[0]).isNotEqualTo((byte) '{');
    }

    @Test
    void output_is_smaller_than_the_json_equivalent_beyond_a_few_fields() {
      // Compatible mode carries each class's schema once per message, so a lone four-field
      // record is not smaller than its JSON; a payload with some repetition comfortably is.
      List<Person> people =
          IntStream.range(0, 20)
              .mapToObj(i -> new Person("Person " + i, 20 + i, i % 2 == 0))
              .toList();
      Order order = new Order("o-1", people);
      byte[] encoded = factory.create(Order.class).encode(order);

      StringBuilder json = new StringBuilder("{\"id\":\"o-1\",\"people\":[");
      for (int i = 0; i < people.size(); i++) {
        Person p = people.get(i);
        if (i > 0) json.append(',');
        json.append("{\"name\":\"")
            .append(p.name())
            .append("\",\"age\":")
            .append(p.age())
            .append(",\"active\":")
            .append(p.active())
            .append('}');
      }
      json.append("]}");

      assertThat(encoded).hasSizeLessThan(json.toString().getBytes(UTF_8).length);
    }
  }

  @Nested
  class Schema_evolution {

    /** {@link Person} with a field added: the same logical type at a later schema version. */
    public record Evolved(String name, int age, boolean active, String email) {}

    @Test
    void a_reader_whose_class_lost_a_field_still_decodes_correctly() {
      // Each factory registers a single class, so Fory gives both the same id; compatible mode's
      // per-field metadata then matches the values by name.
      byte[] bytes =
          ForyCodecFactory.of(Evolved.class)
              .create(Evolved.class)
              .encode(new Evolved("Alice", 30, true, "alice@example.com"));

      Person decoded = ForyCodecFactory.of(Person.class).create(Person.class).decode(bytes);

      assertThat(decoded).isEqualTo(new Person("Alice", 30, true));
    }

    @Test
    void a_reader_whose_class_gained_a_field_reads_it_as_null() {
      byte[] bytes =
          ForyCodecFactory.of(Person.class)
              .create(Person.class)
              .encode(new Person("Alice", 30, true));

      Evolved decoded = ForyCodecFactory.of(Evolved.class).create(Evolved.class).decode(bytes);

      assertThat(decoded).isEqualTo(new Evolved("Alice", 30, true, null));
    }
  }

  @Nested
  class Security_boundary {

    /** A self-referential record, to build a graph deeper than Fory's read-depth limit. */
    public record Node(Node next) {}

    private static Node chain(int depth) {
      Node node = null;
      for (int i = 0; i < depth; i++) {
        node = new Node(node);
      }
      return node;
    }

    @Test
    void a_payload_naming_an_unregistered_class_is_rejected_not_materialised() {
      byte[] bytes =
          ForyCodecFactory.of(Schema_evolution.Evolved.class)
              .create(Schema_evolution.Evolved.class)
              .encode(new Schema_evolution.Evolved("Alice", 30, true, "alice@example.com"));
      ForyCodecFactory reader = ForyCodecFactory.of();
      Codec<Object> codec = reader.create(Object.class);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withCauseInstanceOf(DeserializationException.class);
    }

    @Test
    void a_graph_deeper_than_the_read_limit_is_rejected() {
      Codec<Node> codec = ForyCodecFactory.of(Node.class).create(Node.class);
      byte[] bytes = codec.encode(chain(60));

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withCauseInstanceOf(InsecureException.class)
          .havingCause()
          .withMessageContaining("depth");
    }

    @Test
    void a_rejected_read_does_not_poison_the_instance() {
      Codec<Node> codec = ForyCodecFactory.of(Node.class).create(Node.class);
      byte[] tooDeep = codec.encode(chain(60));
      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(tooDeep));

      Node shallow = chain(10);

      assertThat(codec.decode(codec.encode(shallow))).isEqualTo(shallow);
    }
  }

  @Nested
  class Registration_is_mandatory {

    @Test
    void creating_a_codec_for_an_unregistered_class_fails_fast() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> factory.create(Unregistered.class))
          .withMessageContaining("Unregistered")
          .withMessageContaining("register");
    }

    @Test
    void an_unregistered_type_argument_is_caught_at_creation() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> factory.create(new TypeRef<List<Unregistered>>() {}))
          .withMessageContaining("Unregistered");
    }

    @Test
    void types_fory_handles_without_registration_are_always_supported() {
      assertThat(factory.supports(int.class)).isTrue();
      assertThat(factory.supports(Runnable.class)).isTrue();
      assertThat(factory.supports(Number.class)).isTrue();
      assertThat(factory.supports(byte[].class)).isTrue();
      assertThat(factory.supports(javax.crypto.spec.SecretKeySpec.class)).isTrue();
      assertThat(factory.supports(java.util.ArrayList.class)).isTrue();
    }

    @Test
    void a_wildcard_type_argument_is_not_a_registration_concern() {
      Codec<List<?>> codec = factory.create(new TypeRef<List<?>>() {});
      List<Person> people = List.of(new Person("Alice", 30, true));

      assertThat(codec.decode(codec.encode(people))).isEqualTo(people);
    }

    @Test
    void a_generic_array_type_is_rejected() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> factory.create(new TypeRef<List<String>[]>() {}))
          .withMessageContaining("Unsupported type");
    }

    @Test
    void supports_reports_registration() {
      assertThat(factory.supports(Person.class)).isTrue();
      assertThat(factory.supports(List.class)).isTrue();
      assertThat(factory.supports(String.class)).isTrue();
      assertThat(factory.supports(Unregistered.class)).isFalse();
    }

    @Test
    void a_caller_supplied_fory_without_registration_is_still_a_fory_decision() {
      ThreadSafeFory permissive =
          Fory.builder()
              .withLanguage(Language.JAVA)
              .requireClassRegistration(false)
              .buildThreadSafeFory();
      assertThat(new ForyCodecFactory(permissive).supports(Unregistered.class)).isTrue();
      Codec<Unregistered> codec = new ForyCodecFactory(permissive).create(Unregistered.class);

      // The factory does not second-guess a caller who explicitly relaxed registration.
      assertThat(codec.decode(codec.encode(new Unregistered("x"))))
          .isEqualTo(new Unregistered("x"));
    }
  }

  @Nested
  class Failures {

    @Test
    void decoding_a_value_of_the_wrong_type_is_an_invalid_payload() {
      byte[] person = factory.create(Person.class).encode(new Person("Alice", 30, true));
      Codec<Order> orders = factory.create(Order.class);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> orders.decode(person))
          .withMessageContaining("Person")
          .withMessageContaining("Order");
    }

    @Test
    void corrupt_input_is_an_invalid_payload_whichever_way_fory_reports_it() {
      Codec<Person> codec = factory.create(Person.class);
      byte[] valid = codec.encode(new Person("Alice", 30, true));

      byte[] notFory = "not fory".getBytes(UTF_8);
      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(notFory))
          .withCauseInstanceOf(IllegalArgumentException.class);
      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(new byte[0]))
          .withCauseInstanceOf(IndexOutOfBoundsException.class);
      byte[] truncated = Arrays.copyOf(valid, valid.length / 2);
      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(truncated))
          .withCauseInstanceOf(DeserializationException.class);
    }

    @Test
    void a_corrupt_header_claiming_out_of_band_buffers_is_an_invalid_payload() {
      Codec<Person> codec = factory.create(Person.class);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(new byte[] {2, -1, 28, 0, 22, -107, 122}))
          .withCauseInstanceOf(NullPointerException.class);
    }

    @Test
    void decoding_null_is_a_programmer_error() {
      Codec<Person> codec = factory.create(Person.class);

      assertThatNullPointerException().isThrownBy(() -> codec.decode(null));
    }

    @Test
    void encoding_a_value_of_an_unregistered_class_is_an_invalid_value() {
      Codec<Object> codec = factory.create(Object.class);
      Unregistered unregistered = new Unregistered("x");

      assertThatExceptionOfType(InvalidValueException.class)
          .isThrownBy(() -> codec.encode(unregistered))
          .withMessage("Unable to serialize value")
          .withCauseInstanceOf(InsecureException.class);
    }

    @Test
    void rejects_a_null_fory() {
      assertThatNullPointerException().isThrownBy(() -> new ForyCodecFactory(null));
    }
  }

  @Nested
  class Thread_safety {

    @Test
    void the_default_factory_serializes_concurrently() throws Exception {
      Codec<Person> codec = factory.create(Person.class);
      try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
        List<Future<Boolean>> results =
            IntStream.range(0, 200)
                .mapToObj(
                    i ->
                        pool.submit(
                            () -> {
                              Person p = new Person("p" + i, i, i % 2 == 0);
                              return codec.decode(codec.encode(p)).equals(p);
                            }))
                .toList();
        for (Future<Boolean> result : results) {
          assertThat(result.get()).isTrue();
        }
      }
    }
  }
}
