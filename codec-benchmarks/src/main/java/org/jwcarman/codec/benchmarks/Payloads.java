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

import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Deterministic byte payloads at three sizes: a small JSON record, a medium JSON document, and a
 * large block of prose. Seeded generation keeps every run comparable.
 */
public final class Payloads {

  private static final String[] WORDS = {
    "codec",
    "encode",
    "decode",
    "payload",
    "stream",
    "buffer",
    "byte",
    "frame",
    "block",
    "cache",
    "queue",
    "record",
    "field",
    "value",
    "compress",
    "encrypt",
    "wrap",
    "key",
    "nonce",
    "tag",
    "header",
    "version",
    "format",
    "text",
    "binary",
    "the",
    "of",
    "and",
    "with",
    "into",
    "from",
    "over",
    "under",
    "between",
    "before",
    "after"
  };

  /** A ~120-byte JSON record. */
  public static final byte[] SMALL = json(1);

  /** A ~10 KB JSON document: a list of records. */
  public static final byte[] MEDIUM = json(80);

  /** ~1 MB of pseudo-English prose. */
  public static final byte[] LARGE = prose(1 << 20);

  private Payloads() {}

  /**
   * Looks a payload up by name, for {@code @Param} use.
   *
   * @param name {@code small}, {@code medium} or {@code large}
   * @return the payload
   */
  public static byte[] named(String name) {
    return switch (name) {
      case "small" -> SMALL;
      case "medium" -> MEDIUM;
      case "large" -> LARGE;
      default -> throw new IllegalArgumentException(name);
    };
  }

  private static byte[] json(int records) {
    Random random = new Random(42);
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < records; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append("{\"id\":")
          .append(1000 + i)
          .append(",\"name\":\"")
          .append(WORDS[random.nextInt(WORDS.length)])
          .append(' ')
          .append(WORDS[random.nextInt(WORDS.length)])
          .append('"')
          .append(",\"age\":")
          .append(20 + random.nextInt(50))
          .append(",\"active\":")
          .append(random.nextBoolean())
          .append(",\"email\":\"user")
          .append(i)
          .append("@example.com\"")
          .append(",\"balance\":")
          .append(random.nextInt(100_000))
          .append('.')
          .append(random.nextInt(100))
          .append('}');
    }
    sb.append(']');
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] prose(int size) {
    Random random = new Random(7);
    StringBuilder sb = new StringBuilder(size + 16);
    while (sb.length() < size) {
      sb.append(WORDS[random.nextInt(WORDS.length)]).append(' ');
      if (random.nextInt(12) == 0) {
        sb.append(". ");
      }
    }
    sb.setLength(size);
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }
}
