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

/** Produces {@link Codec} instances for arbitrary types, backed by a serialization library. */
public interface CodecFactory {

  /**
   * Creates a codec for the given type reference, supporting full generic types.
   *
   * @param typeRef the type to create a codec for
   * @param <T> the codec's value type
   * @return a codec for the type
   */
  <T> Codec<T> create(TypeRef<T> typeRef);

  /**
   * Creates a codec for a non-generic class.
   *
   * @param type the class to create a codec for
   * @param <T> the codec's value type
   * @return a codec for the class
   */
  default <T> Codec<T> create(Class<T> type) {
    return create(TypeRef.of(type));
  }
}
