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

import java.util.Objects;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.exception.ForyException;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;
import org.jwcarman.codec.spi.InvalidValueException;

/**
 * A codec that serializes through a {@link ThreadSafeFory} instance. Fory's format is
 * self-describing — the class of every value is written alongside it — so decoding does not need
 * the static type beyond checking that what came back is what the codec was created for. Fory's
 * failures surface as this library's exception families: an unregistered class or other
 * serialization failure is {@link InvalidValueException}, and a corrupt payload or a value of the
 * wrong type is {@link InvalidPayloadException}, in each case with Fory's own exception as the
 * cause.
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
    try {
      return fory.serialize(value);
    } catch (ForyException e) {
      throw new InvalidValueException("Unable to serialize value", e);
    }
  }

  @Override
  public T decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    Object value;
    try {
      value = fory.deserialize(bytes);
    } catch (ForyException
        | IllegalArgumentException
        | IndexOutOfBoundsException
        | NullPointerException e) {
      // Fory reports malformed input four ways: its own exceptions for truncation and
      // security-limit violations, IllegalArgumentException for a buffer it cannot parse at all,
      // IndexOutOfBoundsException for an empty one, and NullPointerException when a corrupted
      // header claims out-of-band buffers. All four mean the same thing here; the null check above
      // keeps a null argument a programmer error rather than a payload rejection.
      throw new InvalidPayloadException("Unable to deserialize payload", e);
    }
    if (value != null && !rawType.isInstance(value)) {
      throw new InvalidPayloadException(
          "Decoded a " + value.getClass().getName() + " but expected " + rawType.getName());
    }
    return (T) value;
  }
}
