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
import java.util.function.Function;

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

  /**
   * Derives a codec for another type from this one, given a conversion in each direction.
   *
   * <p>Where {@link #andThen} wraps the <em>bytes</em> side of a codec, this wraps the
   * <em>value</em> side: the returned codec encodes a {@code U} by converting it to a {@code T}
   * with {@code backward} and then encoding it here, and decodes by decoding a {@code T} here and
   * converting it with {@code forward}. It is the tool for the domain-type-versus-wire-type split —
   * a backend that only serializes generated or registered classes, wrapped as the type the
   * application actually uses:
   *
   * {@snippet lang = java :
   * Codec<Person> codec = factory.create(PersonProto.class).xmap(Person::fromProto, Person::toProto);
   * }
   *
   * <p>Exceptions thrown by either function propagate unchanged.
   *
   * @param forward converts a decoded {@code T} to a {@code U}
   * @param backward converts a {@code U} to the {@code T} this codec encodes
   * @param <U> the derived codec's type
   * @return a codec for {@code U}
   * @throws NullPointerException if either function is null
   */
  /**
   * Wraps this codec so that {@code null} passes straight through in both directions: {@code
   * encode(null)} returns {@code null} and {@code decode(null)} returns {@code null}, without
   * consulting this codec. Every other value is delegated unchanged.
   *
   * <p>Whether a bare codec accepts {@code null} is up to its implementation — a JSON backend
   * encodes it as the literal {@code null}, the transforms reject it — so this is the explicit form
   * for integrations whose contract treats {@code null} as "absent", such as cache serializers that
   * are handed {@code null} on a miss.
   *
   * @return a codec that maps {@code null} to {@code null} and otherwise behaves as this one
   */
  default Codec<T> nullSafe() {
    Codec<T> self = this;
    return new Codec<>() {
      @Override
      public byte[] encode(T value) {
        return value == null ? null : self.encode(value);
      }

      @Override
      public T decode(byte[] bytes) {
        return bytes == null ? null : self.decode(bytes);
      }
    };
  }

  default <U> Codec<U> xmap(
      Function<? super T, ? extends U> forward, Function<? super U, ? extends T> backward) {
    Objects.requireNonNull(forward, "forward must not be null");
    Objects.requireNonNull(backward, "backward must not be null");
    Codec<T> self = this;
    return new Codec<>() {
      @Override
      public byte[] encode(U value) {
        return self.encode(backward.apply(value));
      }

      @Override
      public U decode(byte[] bytes) {
        return forward.apply(self.decode(bytes));
      }
    };
  }
}
