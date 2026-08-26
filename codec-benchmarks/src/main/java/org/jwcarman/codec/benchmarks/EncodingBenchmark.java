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
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.transform.checksum.ChecksumCodec;
import org.jwcarman.codec.transform.encoding.Base32Codec;
import org.jwcarman.codec.transform.encoding.Base64Codec;
import org.jwcarman.codec.transform.encoding.HexCodec;
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

/** The text encodings and the checksum trailer on the medium payload. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class EncodingBenchmark {

  @Param({"base64", "base32", "hex", "crc32c"})
  public String codec;

  private Codec<byte[]> transform;
  private byte[] plain;
  private byte[] encoded;

  @Setup
  public void setup() {
    transform =
        switch (codec) {
          case "base64" -> Base64Codec.basic();
          case "base32" -> Base32Codec.standard();
          case "hex" -> HexCodec.lowerCase();
          case "crc32c" -> ChecksumCodec.crc32c();
          default -> throw new IllegalArgumentException(codec);
        };
    plain = Payloads.MEDIUM;
    encoded = transform.encode(plain);
  }

  @Benchmark
  public byte[] encode() {
    return transform.encode(plain);
  }

  @Benchmark
  public byte[] decode() {
    return transform.decode(encoded);
  }
}
