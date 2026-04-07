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
package org.jwcarman.codec.protobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.TypeRef;

class ProtobufCodecFactoryTest {

  private ProtobufCodecFactory factory;

  @BeforeEach
  void setUp() {
    factory = new ProtobufCodecFactory();
  }

  @Test
  void shouldRoundTripSimpleMessage() {
    Codec<TestMessages.Person> codec = factory.create(TestMessages.Person.class);
    TestMessages.Person original =
        TestMessages.Person.newBuilder().setName("Alice").setAge(30).build();
    byte[] encoded = codec.encode(original);
    TestMessages.Person decoded = codec.decode(encoded);
    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldRoundTripMessageWithNestedType() {
    Codec<TestMessages.Person> codec = factory.create(TestMessages.Person.class);
    TestMessages.Address address =
        TestMessages.Address.newBuilder().setStreet("123 Main St").setCity("Springfield").build();
    TestMessages.Person original =
        TestMessages.Person.newBuilder().setName("Bob").setAge(25).setAddress(address).build();
    byte[] encoded = codec.encode(original);
    TestMessages.Person decoded = codec.decode(encoded);
    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldHandleEmptyMessage() {
    Codec<TestMessages.Person> codec = factory.create(TestMessages.Person.class);
    TestMessages.Person original = TestMessages.Person.getDefaultInstance();
    byte[] encoded = codec.encode(original);
    TestMessages.Person decoded = codec.decode(encoded);
    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldReturnCorrectType() {
    Codec<TestMessages.Person> codec = factory.create(TestMessages.Person.class);
    assertThat(codec.type()).isEqualTo(TestMessages.Person.class);
  }

  @Test
  void shouldWorkWithTypeRef() {
    Codec<TestMessages.Person> codec = factory.create(new TypeRef<TestMessages.Person>() {});
    TestMessages.Person original =
        TestMessages.Person.newBuilder().setName("Charlie").setAge(40).build();
    byte[] encoded = codec.encode(original);
    TestMessages.Person decoded = codec.decode(encoded);
    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldThrowForNonProtobufType() {
    assertThatThrownBy(() -> factory.create(String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not a GeneratedMessageV3 subclass");
  }

  @Test
  void shouldCacheParseFromMethod() {
    Codec<TestMessages.Person> codec1 = factory.create(TestMessages.Person.class);
    Codec<TestMessages.Person> codec2 = factory.create(TestMessages.Person.class);
    TestMessages.Person original =
        TestMessages.Person.newBuilder().setName("Diana").setAge(35).build();
    assertThat(codec1.decode(codec1.encode(original))).isEqualTo(original);
    assertThat(codec2.decode(codec2.encode(original))).isEqualTo(original);
  }

  @Test
  void shouldRoundTripNestedMessageDirectly() {
    Codec<TestMessages.Address> codec = factory.create(TestMessages.Address.class);
    TestMessages.Address original =
        TestMessages.Address.newBuilder().setStreet("456 Oak Ave").setCity("Portland").build();
    byte[] encoded = codec.encode(original);
    TestMessages.Address decoded = codec.decode(encoded);
    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void shouldBeThreadSafe() throws InterruptedException {
    Codec<TestMessages.Person> codec = factory.create(TestMessages.Person.class);
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
                  TestMessages.Person original =
                      TestMessages.Person.newBuilder()
                          .setName("Thread-" + threadIndex + "-" + i)
                          .setAge(threadIndex * 100 + i)
                          .build();
                  byte[] encoded = codec.encode(original);
                  TestMessages.Person decoded = codec.decode(encoded);
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
