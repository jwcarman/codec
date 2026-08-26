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
import org.jwcarman.codec.protobuf.ProtobufCodecFactory;

/**
 * Prints the encoded size of each backend's output for the benchmark payloads as a Markdown table —
 * the companion to {@link BackendBenchmark}, which measures speed. Run with {@code java -cp
 * benchmarks.jar org.jwcarman.codec.benchmarks.EncodedSizes}.
 */
public final class EncodedSizes {

  private EncodedSizes() {}

  /**
   * Entry point.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    StringBuilder out =
        new StringBuilder("| Backend | small (bytes) | medium (bytes) |\n|---|---:|---:|\n");
    for (String backend : List.of("jackson3", "jackson2", "gson", "jsonb", "fory")) {
      var factory = BackendBenchmark.factory(backend);
      out.append("| ")
          .append(backend)
          .append(" | ")
          .append(String.format("%,d", factory.create(Person.class).encode(Person.SAMPLE).length))
          .append(" | ")
          .append(String.format("%,d", factory.create(Order.class).encode(Order.SAMPLE).length))
          .append(" |\n");
    }
    ProtobufCodecFactory protobuf = new ProtobufCodecFactory();
    out.append("| protobuf | ")
        .append(
            String.format(
                "%,d",
                protobuf
                    .create(BenchProtos.PersonProto.class)
                    .encode(Protos.person(Person.SAMPLE))
                    .length))
        .append(" | ")
        .append(
            String.format(
                "%,d",
                protobuf
                    .create(BenchProtos.OrderProto.class)
                    .encode(Protos.order(Order.SAMPLE))
                    .length))
        .append(" |\n");
    System.out.print(out);
  }
}
