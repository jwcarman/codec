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
package org.jwcarman.codec.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.TypeRef;
import tools.jackson.databind.ObjectMapper;

class JacksonCodecFactoryTest {

  private JacksonCodecFactory factory;

  @BeforeEach
  void setUp() {
    factory = new JacksonCodecFactory(new ObjectMapper());
  }

  record Person(String name, int age, boolean active) {}

  @Test
  void shouldRoundTripSimplePojo() {
    Codec<Person> codec = factory.create(Person.class);
    Person original = new Person("Alice", 30, true);

    byte[] encoded = codec.encode(original);
    Person decoded = codec.decode(encoded);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldRoundTripPojoWithNullFields() {
    Codec<Person> codec = factory.create(Person.class);
    Person original = new Person(null, 0, false);

    byte[] encoded = codec.encode(original);
    Person decoded = codec.decode(encoded);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldReturnCorrectTypeForClass() {
    Codec<Person> codec = factory.create(Person.class);
    assertThat(codec.type()).isEqualTo(Person.class);
  }

  @Test
  void shouldRoundTripListOfStrings() {
    Codec<List<String>> codec = factory.create(new TypeRef<List<String>>() {});
    List<String> original = List.of("hello", "world");

    byte[] encoded = codec.encode(original);
    List<String> decoded = codec.decode(encoded);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldRoundTripMapOfStringToInteger() {
    Codec<Map<String, Integer>> codec = factory.create(new TypeRef<Map<String, Integer>>() {});
    Map<String, Integer> original = Map.of("a", 1, "b", 2);

    byte[] encoded = codec.encode(original);
    Map<String, Integer> decoded = codec.decode(encoded);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldRoundTripListOfRecords() {
    Codec<List<Person>> codec = factory.create(new TypeRef<List<Person>>() {});
    List<Person> original = List.of(new Person("Alice", 30, true), new Person("Bob", 25, false));

    byte[] encoded = codec.encode(original);
    List<Person> decoded = codec.decode(encoded);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldHandleEmptyCollections() {
    Codec<List<String>> codec = factory.create(new TypeRef<List<String>>() {});
    List<String> original = List.of();

    byte[] encoded = codec.encode(original);
    List<String> decoded = codec.decode(encoded);

    assertThat(decoded).isEmpty();
  }

  @Test
  void shouldReturnCorrectTypeForTypeRef() {
    Codec<List<String>> codec = factory.create(new TypeRef<List<String>>() {});
    assertThat(codec.type()).isEqualTo(List.class);
  }

  @Test
  void shouldReturnCorrectTypeForMapTypeRef() {
    Codec<Map<String, Integer>> codec = factory.create(new TypeRef<Map<String, Integer>>() {});
    assertThat(codec.type()).isEqualTo(Map.class);
  }

  @Test
  void shouldBeThreadSafe() throws InterruptedException {
    Codec<Person> codec = factory.create(Person.class);
    int threadCount = 10;
    int iterationsPerThread = 100;
    CountDownLatch latch = new CountDownLatch(threadCount);
    CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
      for (int t = 0; t < threadCount; t++) {
        int threadIndex = t;
        executor.submit(
            () -> {
              try {
                for (int i = 0; i < iterationsPerThread; i++) {
                  Person original =
                      new Person(
                          "Thread-" + threadIndex + "-" + i, threadIndex * 100 + i, i % 2 == 0);
                  byte[] encoded = codec.encode(original);
                  Person decoded = codec.decode(encoded);
                  assertThat(decoded).isEqualTo(original);
                }
              } catch (Throwable e) {
                errors.add(e);
              } finally {
                latch.countDown();
              }
            });
      }
      latch.await();
    }

    assertThat(errors).isEmpty();
  }
}
