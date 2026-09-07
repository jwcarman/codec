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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.TypeRef;
import org.jwcarman.codec.transform.compress.GzipCodec;
import org.jwcarman.codec.transform.encoding.Base64Codec;
import tools.jackson.databind.ObjectMapper;

/** Spike: a Jackson → gzip → Base64 chain streams end to end. */
class StreamingSpikeTest {

  record Person(String name, int age) {}

  private final JacksonCodecFactory factory = new JacksonCodecFactory(new ObjectMapper());

  private Codec<List<Person>> chain() {
    return factory
        .create(TypeRef.listOf(TypeRef.of(Person.class)))
        .andThen(new GzipCodec())
        .andThen(Base64Codec.basic());
  }

  @Test
  void round_trips_through_a_file_and_matches_the_array_form(@TempDir Path dir) throws IOException {
    List<Person> people = new ArrayList<>();
    for (int i = 0; i < 20_000; i++) {
      people.add(new Person("person-" + i, i % 90));
    }
    Path file = dir.resolve("people.json.gz.b64");
    Codec<List<Person>> codec = chain();

    try (OutputStream out = Files.newOutputStream(file)) {
      codec.encodeTo(people, out);
    }
    List<Person> back;
    try (InputStream in = Files.newInputStream(file)) {
      back = codec.decodeFrom(in);
    }

    assertThat(back).isEqualTo(people);
    assertThat(Files.readAllBytes(file)).isEqualTo(codec.encode(people));
    assertThat(codec.decode(Files.readAllBytes(file))).isEqualTo(people);
  }

  /**
   * A bean whose second property observes the sink while Jackson is still serialising the first.
   */
  public static class Observer {
    private final String head;
    private final AtomicLong arrived;
    long observedAtTail = -1;

    Observer(String head, AtomicLong arrived) {
      this.head = head;
      this.arrived = arrived;
    }

    public String getHead() {
      return head;
    }

    public long getTail() {
      observedAtTail = arrived.get();
      return observedAtTail;
    }
  }

  @Test
  void bytes_reach_the_sink_before_the_value_is_fully_serialised() throws IOException {
    Random random = new Random(1);
    StringBuilder head = new StringBuilder();
    for (int i = 0; i < 1_000_000; i++) {
      head.append((char) ('a' + random.nextInt(26)));
    }
    AtomicLong arrived = new AtomicLong();
    OutputStream counting =
        new OutputStream() {
          @Override
          public void write(int b) {
            arrived.incrementAndGet();
          }

          @Override
          public void write(byte[] b, int off, int len) {
            arrived.addAndGet(len);
          }
        };
    Codec<Observer> codec =
        factory.create(Observer.class).andThen(new GzipCodec()).andThen(Base64Codec.basic());
    Observer observer = new Observer(head.toString(), arrived);

    codec.encodeTo(observer, counting);

    // When Jackson read the tail property, most of the head had already been compressed, Base64
    // encoded and delivered to the sink: the chain streamed rather than buffered.
    long total = arrived.get();
    assertThat(observer.observedAtTail).isPositive().isLessThan(total);
    assertThat(observer.observedAtTail).isGreaterThan(total / 2);
  }
}
