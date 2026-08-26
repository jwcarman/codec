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
import org.apache.kafka.common.serialization.Serializer;
import org.jwcarman.codec.spi.Codec;

/**
 * Adapts a {@link Codec} to Kafka's {@link Serializer}. The topic is ignored — a codec does not
 * vary by topic — and {@code null} serializes to {@code null}, Kafka's tombstone, through {@link
 * Codec#nullSafe()}. Everything else, including any exception the codec throws, passes through
 * unchanged.
 *
 * <p>Kafka's reflective, no-arg configuration path ({@code value.serializer=...}) cannot construct
 * this class; hand instances to the producer or {@code ProducerFactory} directly.
 *
 * @param <T> the value type
 */
public final class CodecSerializer<T> implements Serializer<T> {

  private final Codec<T> codec;

  /**
   * Creates a serializer backed by the codec.
   *
   * @param codec the codec to serialize with
   * @throws NullPointerException if {@code codec} is null
   */
  public CodecSerializer(Codec<T> codec) {
    this.codec = Objects.requireNonNull(codec, "codec must not be null").nullSafe();
  }

  @Override
  public byte[] serialize(String topic, T data) {
    return codec.encode(data);
  }
}
