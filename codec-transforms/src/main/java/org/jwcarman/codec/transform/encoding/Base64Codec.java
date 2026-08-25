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

import java.util.Base64;
import java.util.Objects;
import org.jwcarman.codec.spi.Codec;

/**
 * A byte-to-text-safe-byte transform: encodes any bytes as Base64 (RFC 4648) and decodes them back.
 * The output is still a {@code byte[]}, but one made only of ASCII characters, so it survives
 * places that mangle raw bytes — text columns, JSON string fields, URLs, log lines. Turn it into a
 * {@link String} with {@code new String(bytes, StandardCharsets.US_ASCII)}.
 *
 * <p>Put this transform <em>last</em> in a chain: Base64 expands its input by a third, so compress
 * and encrypt first, then make the result text-safe:
 *
 * <pre>{@code
 * Codec<Order> codec = factory.create(Order.class)
 *     .andThen(new GzipCodec())
 *     .andThen(Base64Codec.urlSafeWithoutPadding());
 * }</pre>
 *
 * <p>Decoding is strict: input containing characters outside the variant's alphabet is rejected
 * with {@link IllegalArgumentException} rather than decoded to garbage.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class Base64Codec implements Codec<byte[]> {

  private final Base64.Encoder encoder;
  private final Base64.Decoder decoder;

  private Base64Codec(Base64.Encoder encoder, Base64.Decoder decoder) {
    this.encoder = encoder;
    this.decoder = decoder;
  }

  /**
   * The basic alphabet ({@code A-Za-z0-9+/}) with padding.
   *
   * @return a codec using the basic variant
   */
  public static Base64Codec basic() {
    return new Base64Codec(Base64.getEncoder(), Base64.getDecoder());
  }

  /**
   * The URL- and filename-safe alphabet ({@code A-Za-z0-9-_}) with padding.
   *
   * @return a codec using the URL-safe variant
   */
  public static Base64Codec urlSafe() {
    return new Base64Codec(Base64.getUrlEncoder(), Base64.getUrlDecoder());
  }

  /**
   * The URL- and filename-safe alphabet without padding — the form most often wanted for tokens and
   * query-string values. Decoding accepts input with or without padding.
   *
   * @return a codec using the URL-safe variant without padding
   */
  public static Base64Codec urlSafeWithoutPadding() {
    return new Base64Codec(Base64.getUrlEncoder().withoutPadding(), Base64.getUrlDecoder());
  }

  /**
   * The MIME variant: the basic alphabet with output wrapped at 76 characters using CRLF line
   * separators, as used in email bodies. Decoding ignores line separators.
   *
   * @return a codec using the MIME variant
   */
  public static Base64Codec mime() {
    return new Base64Codec(Base64.getMimeEncoder(), Base64.getMimeDecoder());
  }

  @Override
  public byte[] encode(byte[] value) {
    Objects.requireNonNull(value, "value must not be null");
    return encoder.encode(value);
  }

  @Override
  public byte[] decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    return decoder.decode(bytes);
  }
}
