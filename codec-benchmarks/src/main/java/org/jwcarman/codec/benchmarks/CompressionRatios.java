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

import java.util.List;
import org.jwcarman.codec.spi.Codec;

/**
 * Prints the compressed size and ratio of every compression transform at every payload size as a
 * Markdown table — the companion to {@link CompressionBenchmark}, which measures speed. Run with
 * {@code java -cp benchmarks.jar org.jwcarman.codec.benchmarks.CompressionRatios}.
 */
public final class CompressionRatios {

  private CompressionRatios() {}

  /**
   * Entry point.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    List<String> payloads = List.of("small", "medium", "large");
    StringBuilder out = new StringBuilder("| Transform |");
    payloads.forEach(p -> out.append(' ').append(p).append(" |"));
    out.append("\n|---|");
    payloads.forEach(p -> out.append("---|"));
    out.append('\n');
    for (String name :
        List.of(
            "gzip",
            "deflate1",
            "deflate6",
            "deflate9",
            "zstd1",
            "zstd3",
            "zstd9",
            "zstd19",
            "lz4",
            "lz4hc")) {
      Codec<byte[]> transform = CompressionBenchmark.transform(name);
      out.append("| ").append(name).append(" |");
      for (String payload : payloads) {
        byte[] plain = Payloads.named(payload);
        int size = transform.encode(plain).length;
        out.append(String.format(" %,d (%.1f%%) |", size, 100.0 * size / plain.length));
      }
      out.append('\n');
    }
    System.out.print(out);
  }
}
