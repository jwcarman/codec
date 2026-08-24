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

public interface Codec<T> {
  byte[] encode(T value);

  T decode(byte[] bytes);

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
