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
package org.jwcarman.codec.benchmarks;

import com.google.gson.Gson;
import jakarta.json.bind.JsonbBuilder;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jwcarman.codec.fory.ForyCodecFactory;
import org.jwcarman.codec.gson.GsonCodecFactory;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.jackson2.Jackson2CodecFactory;
import org.jwcarman.codec.jsonb.JsonbCodecFactory;
import org.jwcarman.codec.protobuf.ProtobufCodecFactory;
import org.jwcarman.codec.spi.Codec;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Every backend encoding and decoding the same record. Protobuf serializes its generated message
 * rather than the record — the closest like-for-like comparison the format allows.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class BackendBenchmark {

  @Param({"jackson3", "jackson2", "gson", "jsonb", "fory", "protobuf"})
  public String backend;

  private Supplier<byte[]> encoder;
  private Supplier<Object> decoder;

  @Setup
  public void setup() {
    if ("protobuf".equals(backend)) {
      BenchProtos.PersonProto proto =
          BenchProtos.PersonProto.newBuilder()
              .setName(Person.SAMPLE.name())
              .setAge(Person.SAMPLE.age())
              .setActive(Person.SAMPLE.active())
              .setEmail(Person.SAMPLE.email())
              .build();
      bind(new ProtobufCodecFactory().create(BenchProtos.PersonProto.class), proto);
    } else {
      Codec<Person> people =
          switch (backend) {
            case "jackson3" ->
                new JacksonCodecFactory(new tools.jackson.databind.ObjectMapper())
                    .create(Person.class);
            case "jackson2" ->
                new Jackson2CodecFactory(new com.fasterxml.jackson.databind.ObjectMapper())
                    .create(Person.class);
            case "gson" -> new GsonCodecFactory(new Gson()).create(Person.class);
            case "jsonb" -> new JsonbCodecFactory(JsonbBuilder.create()).create(Person.class);
            case "fory" -> ForyCodecFactory.of(Person.class).create(Person.class);
            default -> throw new IllegalArgumentException(backend);
          };
      bind(people, Person.SAMPLE);
    }
  }

  private <T> void bind(Codec<T> codec, T value) {
    byte[] encoded = codec.encode(value);
    encoder = () -> codec.encode(value);
    decoder = () -> codec.decode(encoded);
  }

  @Benchmark
  public byte[] encode() {
    return encoder.get();
  }

  @Benchmark
  public Object decode() {
    return decoder.get();
  }
}
