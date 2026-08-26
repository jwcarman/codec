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

import java.util.concurrent.TimeUnit;
import org.jwcarman.codec.lz4.Lz4Codec;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.transform.compress.DeflateCodec;
import org.jwcarman.codec.transform.compress.GzipCodec;
import org.jwcarman.codec.zstd.ZstdCodec;
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
 * Every compression transform, encode and decode, at each payload size. Deflate is benchmarked at
 * levels 1, 6 (the default) and 9; gzip shares its engine and adds only framing, so it appears at
 * the default level alone.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CompressionBenchmark {

  @Param({
    "gzip",
    "deflate1",
    "deflate6",
    "deflate9",
    "zstd1",
    "zstd3",
    "zstd9",
    "zstd19",
    "lz4",
    "lz4hc"
  })
  public String codec;

  @Param({"small", "medium", "large"})
  public String payload;

  private static final long MAX_DECODED = 64L * 1024 * 1024;

  private Codec<byte[]> transform;
  private byte[] plain;
  private byte[] compressed;

  /**
   * Resolves a compression transform by benchmark name.
   *
   * @param name the {@code @Param} value
   * @return the transform
   */
  public static Codec<byte[]> transform(String name) {
    return switch (name) {
      case "gzip" -> new GzipCodec();
      case "deflate1" -> new DeflateCodec(1, MAX_DECODED);
      case "deflate6" -> new DeflateCodec(6, MAX_DECODED);
      case "deflate9" -> new DeflateCodec(9, MAX_DECODED);
      case "zstd1" -> new ZstdCodec(1);
      case "zstd3" -> new ZstdCodec(3);
      case "zstd9" -> new ZstdCodec(9);
      case "zstd19" -> new ZstdCodec(19);
      case "lz4" -> new Lz4Codec();
      case "lz4hc" -> Lz4Codec.highCompression();
      default -> throw new IllegalArgumentException(name);
    };
  }

  @Setup
  public void setup() {
    transform = transform(codec);
    plain = Payloads.named(payload);
    compressed = transform.encode(plain);
  }

  @Benchmark
  public byte[] encode() {
    return transform.encode(plain);
  }

  @Benchmark
  public byte[] decode() {
    return transform.decode(compressed);
  }
}
