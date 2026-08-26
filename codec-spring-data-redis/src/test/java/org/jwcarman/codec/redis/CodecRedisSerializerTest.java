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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.ByteBuffer;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.codec.spi.TypeRef;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CodecRedisSerializerTest {

  private static final Codec<String> UTF8 =
      new Codec<>() {
        @Override
        public byte[] encode(String value) {
          return value.getBytes(UTF_8);
        }

        @Override
        public String decode(byte[] bytes) {
          return new String(bytes, UTF_8);
        }
      };

  private static final Codec<UUID> UUIDS = UTF8.xmap(UUID::fromString, UUID::toString);

  @Nested
  class Adapting {

    @Test
    void serializes_through_the_codec() {
      assertThat(CodecRedisSerializer.of(UTF8).serialize("hi")).isEqualTo(UTF8.encode("hi"));
    }

    @Test
    void deserializes_through_the_codec() {
      assertThat(CodecRedisSerializer.of(UTF8).deserialize(UTF8.encode("hi"))).isEqualTo("hi");
    }

    @Test
    void null_is_absent_in_both_directions() {
      CodecRedisSerializer<String> serializer = CodecRedisSerializer.of(UTF8);

      assertThat(serializer.serialize(null)).isNull();
      assertThat(serializer.deserialize(null)).isNull();
    }

    @Test
    void codec_exceptions_pass_through_unchanged() {
      CodecRedisSerializer<UUID> serializer = CodecRedisSerializer.of(UUIDS);
      byte[] notAUuid = UTF8.encode("nope");

      assertThatIllegalArgumentException().isThrownBy(() -> serializer.deserialize(notAUuid));
    }
  }

  @Nested
  class Target_type {

    @Test
    void is_object_when_the_type_is_not_given() {
      assertThat(CodecRedisSerializer.of(UTF8).getTargetType()).isEqualTo(Object.class);
    }

    @Test
    void is_the_given_type() {
      CodecRedisSerializer<UUID> serializer = CodecRedisSerializer.of(UUIDS, UUID.class);

      assertThat(serializer.getTargetType()).isEqualTo(UUID.class);
      assertThat(serializer.canSerialize(UUID.class)).isTrue();
      assertThat(serializer.canSerialize(String.class)).isFalse();
    }

    @Test
    void is_taken_from_the_factory_form() {
      CodecFactory factory =
          new CodecFactory() {
            @Override
            public <T> Codec<T> create(TypeRef<T> type) {
              throw new UnsupportedOperationException();
            }

            @Override
            public <T> Codec<T> create(Class<T> type) {
              return UTF8.xmap(s -> type.cast(UUID.fromString(s)), Object::toString);
            }
          };

      CodecRedisSerializer<UUID> serializer = CodecRedisSerializer.of(factory, UUID.class);
      UUID id = UUID.randomUUID();

      assertThat(serializer.getTargetType()).isEqualTo(UUID.class);
      assertThat(serializer.deserialize(serializer.serialize(id))).isEqualTo(id);
    }
  }

  @Nested
  class Serialization_pair {

    @Test
    void reads_and_writes_through_the_serializer() {
      SerializationPair<String> pair = CodecRedisSerializer.of(UTF8).serializationPair();

      ByteBuffer written = pair.write("hi");

      assertThat(written).isEqualTo(ByteBuffer.wrap(UTF8.encode("hi")));
      assertThat(pair.read(ByteBuffer.wrap(UTF8.encode("hi")))).isEqualTo("hi");
    }
  }

  @Nested
  class Validation {

    @Test
    void rejects_nulls() {
      assertThatNullPointerException()
          .isThrownBy(() -> CodecRedisSerializer.of((Codec<String>) null));
      assertThatNullPointerException()
          .isThrownBy(() -> CodecRedisSerializer.of((Codec<String>) null, String.class));
      assertThatNullPointerException().isThrownBy(() -> CodecRedisSerializer.of(UTF8, null));
      assertThatNullPointerException()
          .isThrownBy(() -> CodecRedisSerializer.of((CodecFactory) null, String.class));
    }
  }
}
