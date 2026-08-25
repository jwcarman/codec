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
import static org.assertj.core.api.Assertions.assertThatRuntimeException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
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
    void output_is_smaller_than_the_json_equivalent() {
      Person alice = new Person("Alice", 30, true);
      byte[] encoded = factory.create(Person.class).encode(alice);
      byte[] json = "{\"name\":\"Alice\",\"age\":30,\"active\":true}".getBytes(UTF_8);

      assertThat(encoded.length).isLessThanOrEqualTo(json.length);
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
    void decoding_a_value_of_the_wrong_type_is_a_class_cast_exception() {
      byte[] person = factory.create(Person.class).encode(new Person("Alice", 30, true));
      Codec<Order> orders = factory.create(Order.class);

      assertThatExceptionOfType(ClassCastException.class)
          .isThrownBy(() -> orders.decode(person))
          .withMessageContaining("Person")
          .withMessageContaining("Order");
    }

    @Test
    void corrupt_input_is_rejected() {
      Codec<Person> codec = factory.create(Person.class);

      assertThatRuntimeException().isThrownBy(() -> codec.decode("not fory".getBytes(UTF_8)));
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
