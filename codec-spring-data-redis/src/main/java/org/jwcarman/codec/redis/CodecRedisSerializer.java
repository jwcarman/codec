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
package org.jwcarman.codec.redis;

import java.util.Objects;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Adapts a {@link Codec} to Spring Data Redis's {@link RedisSerializer}, so a codec — with whatever
 * compression or encryption it composes — is what Redis stores:
 *
 * {@snippet lang = java :
 * RedisTemplate<String, Person> template = new RedisTemplate<>();
 * template.setValueSerializer(CodecRedisSerializer.of(factory, Person.class));
 * }
 *
 * <p>{@code RedisSerializer}'s contract treats {@code null} as absent — it is handed {@code null}
 * on a cache miss and for a {@code null} value — so the codec is wrapped with {@link
 * Codec#nullSafe()}. Everything else, including any exception the codec throws, passes through
 * unchanged.
 *
 * @param <T> the value type
 */
public final class CodecRedisSerializer<T> implements RedisSerializer<T> {

  private final Codec<T> codec;
  private final Class<?> targetType;

  private CodecRedisSerializer(Codec<T> codec, Class<?> targetType) {
    this.codec = codec.nullSafe();
    this.targetType = targetType;
  }

  /**
   * Adapts a codec whose value type is not known statically; {@link #getTargetType()} reports
   * {@code Object}.
   *
   * @param codec the codec to adapt
   * @param <T> the value type
   * @return a serializer backed by the codec
   * @throws NullPointerException if {@code codec} is null
   */
  public static <T> CodecRedisSerializer<T> of(Codec<T> codec) {
    return new CodecRedisSerializer<>(
        Objects.requireNonNull(codec, "codec must not be null"), Object.class);
  }

  /**
   * Adapts a codec for a known value type, which Spring Data consults through {@link
   * #getTargetType()} and {@link #canSerialize(Class)}.
   *
   * @param codec the codec to adapt
   * @param type the value type
   * @param <T> the value type
   * @return a serializer backed by the codec
   * @throws NullPointerException if either argument is null
   */
  public static <T> CodecRedisSerializer<T> of(Codec<T> codec, Class<T> type) {
    return new CodecRedisSerializer<>(
        Objects.requireNonNull(codec, "codec must not be null"),
        Objects.requireNonNull(type, "type must not be null"));
  }

  /**
   * Creates a codec for the type from the factory and adapts it.
   *
   * @param factory the factory to create the codec with
   * @param type the value type
   * @param <T> the value type
   * @return a serializer backed by the factory's codec for the type
   * @throws NullPointerException if either argument is null
   */
  public static <T> CodecRedisSerializer<T> of(CodecFactory factory, Class<T> type) {
    Objects.requireNonNull(factory, "factory must not be null");
    Objects.requireNonNull(type, "type must not be null");
    return of(factory.create(type), type);
  }

  /**
   * This serializer as the {@link SerializationPair} the cache abstraction expects:
   *
   * {@snippet lang = java :
   * RedisCacheConfiguration.defaultCacheConfig()
   *     .serializeValuesWith(CodecRedisSerializer.of(factory, Person.class).serializationPair());
   * }
   *
   * @return a serialization pair reading and writing through this serializer
   */
  public SerializationPair<T> serializationPair() {
    return SerializationPair.fromSerializer(this);
  }

  @Override
  public byte[] serialize(T value) {
    return codec.encode(value);
  }

  @Override
  public T deserialize(byte[] bytes) {
    return codec.decode(bytes);
  }

  @Override
  public Class<?> getTargetType() {
    return targetType;
  }
}
