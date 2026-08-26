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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serde;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.Codec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CodecKafkaAdaptersTest {

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
  class Serializer {

    @Test
    void serializes_through_the_codec_ignoring_the_topic() {
      CodecSerializer<String> serializer = new CodecSerializer<>(UTF8);

      assertThat(serializer.serialize("any-topic", "hi")).isEqualTo(UTF8.encode("hi"));
      assertThat(serializer.serialize("other-topic", "hi")).isEqualTo(UTF8.encode("hi"));
    }

    @Test
    void a_null_value_is_a_tombstone() {
      assertThat(new CodecSerializer<>(UTF8).serialize("t", null)).isNull();
    }

    @Test
    void rejects_a_null_codec() {
      assertThatNullPointerException().isThrownBy(() -> new CodecSerializer<>(null));
    }
  }

  @Nested
  class Deserializer {

    @Test
    void deserializes_through_the_codec_ignoring_the_topic() {
      CodecDeserializer<String> deserializer = new CodecDeserializer<>(UTF8);

      assertThat(deserializer.deserialize("any-topic", UTF8.encode("hi"))).isEqualTo("hi");
    }

    @Test
    void a_tombstone_deserializes_to_null() {
      assertThat(new CodecDeserializer<>(UTF8).deserialize("t", null)).isNull();
    }

    @Test
    void codec_exceptions_pass_through_unchanged() {
      CodecDeserializer<UUID> deserializer = new CodecDeserializer<>(UUIDS);
      byte[] notAUuid = UTF8.encode("nope");

      assertThatIllegalArgumentException()
          .isThrownBy(() -> deserializer.deserialize("t", notAUuid));
    }

    @Test
    void rejects_a_null_codec() {
      assertThatNullPointerException().isThrownBy(() -> new CodecDeserializer<>(null));
    }
  }

  @Nested
  class Serde_pair {

    @Test
    void round_trips_through_its_serializer_and_deserializer() {
      Serde<UUID> serde = new CodecSerde<>(UUIDS);
      UUID id = UUID.randomUUID();

      byte[] bytes = serde.serializer().serialize("t", id);

      assertThat(serde.deserializer().deserialize("t", bytes)).isEqualTo(id);
    }

    @Test
    void configure_and_close_are_no_ops() {
      Serde<String> serde = new CodecSerde<>(UTF8);

      serde.configure(Map.of("ignored", "x"), false);
      serde.close();

      assertThat(serde.serializer().serialize("t", "still works"))
          .isEqualTo(UTF8.encode("still works"));
    }

    @Test
    void rejects_a_null_codec() {
      assertThatNullPointerException().isThrownBy(() -> new CodecSerde<>(null));
    }
  }
}
