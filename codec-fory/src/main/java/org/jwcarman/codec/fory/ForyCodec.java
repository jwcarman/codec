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
package org.jwcarman.codec.fory;

import org.apache.fory.ThreadSafeFory;
import org.jwcarman.codec.spi.Codec;

/**
 * A codec that serializes through a {@link ThreadSafeFory} instance. Fory's format is
 * self-describing — the class of every value is written alongside it — so decoding does not need
 * the static type beyond checking that what came back is what the codec was created for. Fory's own
 * runtime exceptions surface unchanged: an unregistered class, a corrupt payload, or a value of the
 * wrong type is reported by Fory, not translated here.
 */
class ForyCodec<T> implements Codec<T> {

  private final ThreadSafeFory fory;
  private final Class<?> rawType;

  ForyCodec(ThreadSafeFory fory, Class<?> rawType) {
    this.fory = fory;
    this.rawType = rawType;
  }

  @Override
  public byte[] encode(T value) {
    return fory.serialize(value);
  }

  @Override
  public T decode(byte[] bytes) {
    Object value = fory.deserialize(bytes);
    if (value != null && !rawType.isInstance(value)) {
      throw new ClassCastException(
          "Decoded a " + value.getClass().getName() + " but expected " + rawType.getName());
    }
    return (T) value;
  }
}
