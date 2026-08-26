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
package org.jwcarman.codec.kafka;

import java.util.Objects;
import org.apache.kafka.common.serialization.Deserializer;
import org.jwcarman.codec.spi.Codec;

/**
 * Adapts a {@link Codec} to Kafka's {@link Deserializer}. The topic is ignored, and {@code null} —
 * a tombstone — deserializes to {@code null} through {@link Codec#nullSafe()}. Everything else,
 * including any exception the codec throws, passes through unchanged.
 *
 * <p>Kafka's reflective, no-arg configuration path ({@code value.deserializer=...}) cannot
 * construct this class; hand instances to the consumer or {@code ConsumerFactory} directly.
 *
 * @param <T> the value type
 */
public final class CodecDeserializer<T> implements Deserializer<T> {

  private final Codec<T> codec;

  /**
   * Creates a deserializer backed by the codec.
   *
   * @param codec the codec to deserialize with
   * @throws NullPointerException if {@code codec} is null
   */
  public CodecDeserializer(Codec<T> codec) {
    this.codec = Objects.requireNonNull(codec, "codec must not be null").nullSafe();
  }

  @Override
  public T deserialize(String topic, byte[] data) {
    return codec.decode(data);
  }
}
