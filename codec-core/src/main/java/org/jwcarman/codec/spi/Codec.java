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
package org.jwcarman.codec.spi;

import java.util.Objects;

/**
 * Converts values of type {@code T} to and from {@code byte[]}.
 *
 * <p>Implementations must be symmetric: {@code decode(encode(value))} yields a value equal to the
 * original. Codecs are expected to be thread-safe.
 *
 * @param <T> the type this codec converts
 */
public interface Codec<T> {

  /**
   * Encodes a value to bytes.
   *
   * @param value the value to encode
   * @return the encoded bytes
   */
  byte[] encode(T value);

  /**
   * Decodes bytes back into a value.
   *
   * @param bytes the bytes to decode
   * @return the decoded value
   */
  T decode(byte[] bytes);

  /**
   * Layers a byte-level transform (compression, encryption, etc.) onto this codec.
   *
   * <p>The returned codec applies {@code transform.encode} after this codec's {@code encode}, and
   * {@code transform.decode} before this codec's {@code decode}. Chained transforms compose outward
   * on encode and unwind automatically in reverse order on decode:
   *
   * {@snippet lang = java :
   * Codec<Person> codec = factory.create(Person.class).andThen(new GzipCodec()).andThen(aes);
   * }
   *
   * @param transform the byte transform to apply after encoding (and invert before decoding)
   * @return a codec of the same type with the transform applied
   * @throws NullPointerException if {@code transform} is null
   */
  default Codec<T> andThen(Codec<byte[]> transform) {
    Objects.requireNonNull(transform, "transform must not be null");
    Codec<T> self = this;
    return new Codec<>() {
      @Override
      public byte[] encode(T value) {
        return transform.encode(self.encode(value));
      }

      @Override
      public T decode(byte[] bytes) {
        return self.decode(transform.decode(bytes));
      }
    };
  }
}
