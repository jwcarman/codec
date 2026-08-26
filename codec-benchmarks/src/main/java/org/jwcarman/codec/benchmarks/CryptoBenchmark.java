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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import org.jwcarman.codec.crypto.BoundedDataKeyStrategy;
import org.jwcarman.codec.crypto.EnvelopeCodec;
import org.jwcarman.codec.crypto.JceDataKeyProvider;
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
 * Envelope encryption with a fresh data key per message ({@code direct}) versus one amortised over
 * many messages ({@code bounded}) — the cost of a KEK wrap per message is the number the bounded
 * strategy exists to remove.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CryptoBenchmark {

  @Param({"direct", "bounded"})
  public String strategy;

  @Param({"small", "medium"})
  public String payload;

  private EnvelopeCodec codec;
  private byte[] plain;
  private byte[] sealed;

  @Setup
  public void setup() {
    byte[] kek = new byte[32];
    for (int i = 0; i < kek.length; i++) {
      kek[i] = (byte) i;
    }
    JceDataKeyProvider keys =
        new JceDataKeyProvider("bench", Map.of("bench", new SecretKeySpec(kek, "AES")));
    EnvelopeCodec.Builder builder = EnvelopeCodec.builder(keys);
    if ("bounded".equals(strategy)) {
      builder.strategy(new BoundedDataKeyStrategy(1L << 20, Duration.ofHours(1)));
    }
    codec = builder.build();
    plain = Payloads.named(payload);
    sealed = codec.encode(plain);
  }

  @Benchmark
  public byte[] encode() {
    return codec.encode(plain);
  }

  @Benchmark
  public byte[] decode() {
    return codec.decode(sealed);
  }
}
