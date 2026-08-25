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
package org.jwcarman.codec.transform.encoding;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import org.jwcarman.codec.spi.Codec;

/**
 * A byte-to-text-safe-byte transform using hexadecimal (RFC 4648 base16): two ASCII digits per
 * input byte. Twice the size of the input — larger than Base64 — but trivially readable and
 * copy-pasteable, which makes it the right choice for identifiers, checksums, keys in
 * configuration, and anything a person will look at.
 *
 * <p>Like {@link Base64Codec}, put it <em>last</em> in a chain. Decoding is strict and
 * case-insensitive: odd-length input or a non-hex character is rejected with {@link
 * IllegalArgumentException}.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class HexCodec implements Codec<byte[]> {

  private final HexFormat format;

  private HexCodec(HexFormat format) {
    this.format = format;
  }

  /**
   * Lower-case digits ({@code 0-9a-f}), the usual convention in Java and most tooling.
   *
   * @return a codec emitting lower-case hex
   */
  public static HexCodec lowerCase() {
    return new HexCodec(HexFormat.of());
  }

  /**
   * Upper-case digits ({@code 0-9A-F}), the form RFC 4648 uses in its examples.
   *
   * @return a codec emitting upper-case hex
   */
  public static HexCodec upperCase() {
    return new HexCodec(HexFormat.of().withUpperCase());
  }

  @Override
  public byte[] encode(byte[] value) {
    Objects.requireNonNull(value, "value must not be null");
    return format.formatHex(value).getBytes(StandardCharsets.US_ASCII);
  }

  @Override
  public byte[] decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    return format.parseHex(new String(bytes, StandardCharsets.US_ASCII));
  }
}
