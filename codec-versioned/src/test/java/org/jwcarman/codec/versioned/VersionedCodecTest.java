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
package org.jwcarman.codec.versioned;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Locale;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jwcarman.codec.spi.Codec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class VersionedCodecTest {

  private static final byte MAGIC_0 = (byte) 0xC0;
  private static final byte MAGIC_1 = (byte) 0xDC;

  /** Encodes a string as UTF-8, upper-cased on the way out so v1 and v2 payloads differ. */
  private static Codec<String> upperCase() {
    return new Codec<>() {
      @Override
      public byte[] encode(String value) {
        return value.toUpperCase(Locale.ROOT).getBytes(UTF_8);
      }

      @Override
      public String decode(byte[] bytes) {
        return new String(bytes, UTF_8).toLowerCase(Locale.ROOT);
      }
    };
  }

  private static Codec<String> plain() {
    return new Codec<>() {
      @Override
      public byte[] encode(String value) {
        return value.getBytes(UTF_8);
      }

      @Override
      public String decode(byte[] bytes) {
        return new String(bytes, UTF_8);
      }
    };
  }

  @Nested
  class A_versioned_codec {

    @Test
    void round_trips_through_the_write_version() {
      Codec<String> codec = VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

      assertThat(codec.decode(codec.encode("hello"))).isEqualTo("hello");
    }

    @Test
    void prefixes_the_payload_with_magic_and_the_write_version() {
      Codec<String> codec =
          VersionedCodec.<String>builder()
              .version(1, plain())
              .version(2, plain())
              .writing(2)
              .build();

      byte[] encoded = codec.encode("hi");

      assertThat(encoded).containsExactly(MAGIC_0, MAGIC_1, 2, 'h', 'i');
    }

    @Test
    void reads_a_payload_written_at_an_older_version() {
      Codec<String> writer =
          VersionedCodec.<String>builder().version(1, plain()).writing(1).build();
      Codec<String> reader =
          VersionedCodec.<String>builder()
              .version(1, plain())
              .version(2, upperCase())
              .writing(2)
              .build();

      assertThat(reader.decode(writer.encode("hello"))).isEqualTo("hello");
    }

    @Test
    void dispatches_each_version_to_its_own_codec() {
      Codec<String> codec =
          VersionedCodec.<String>builder()
              .version(1, plain())
              .version(2, upperCase())
              .writing(2)
              .build();

      byte[] encoded = codec.encode("hello");

      assertThat(encoded).containsExactly(MAGIC_0, MAGIC_1, 2, 'H', 'E', 'L', 'L', 'O');
      assertThat(codec.decode(encoded)).isEqualTo("hello");
    }

    @Test
    void hands_an_empty_payload_to_the_delegate() {
      Codec<String> codec = VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

      assertThat(codec.decode(new byte[] {MAGIC_0, MAGIC_1, 1})).isEmpty();
    }

    @Test
    void rejects_a_null_value_on_encode() {
      Codec<String> codec = VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

      assertThatNullPointerException().isThrownBy(() -> codec.encode(null));
    }

    @Test
    void rejects_a_null_buffer_on_decode() {
      Codec<String> codec = VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

      assertThatNullPointerException().isThrownBy(() -> codec.decode(null));
    }

    @Test
    void passes_null_through_when_wrapped_null_safe() {
      Codec<String> codec =
          VersionedCodec.<String>builder().version(1, plain()).writing(1).build().nullSafe();

      assertThat(codec.encode(null)).isNull();
      assertThat(codec.decode(null)).isNull();
    }

    @Test
    void propagates_a_delegate_failure_unchanged() {
      Codec<String> exploding =
          new Codec<>() {
            @Override
            public byte[] encode(String value) {
              throw new IllegalStateException("boom");
            }

            @Override
            public String decode(byte[] bytes) {
              throw new IllegalStateException("boom");
            }
          };
      Codec<String> codec =
          VersionedCodec.<String>builder().version(1, exploding).writing(1).build();

      assertThatIllegalStateException().isThrownBy(() -> codec.encode("hi")).withMessage("boom");
      assertThatIllegalStateException()
          .isThrownBy(() -> codec.decode(new byte[] {MAGIC_0, MAGIC_1, 1}))
          .withMessage("boom");
    }
  }

  @Nested
  class A_buffer_that_is_not_a_versioned_payload {

    private final Codec<String> codec =
        VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

    @Test
    void is_rejected_when_empty() {
      assertThatExceptionOfType(VersionedFormatException.class)
          .isThrownBy(() -> codec.decode(new byte[0]))
          .withMessage("not a versioned payload: expected at least 3 bytes, got 0");
    }

    @ParameterizedTest(name = "{0} byte(s)")
    @ValueSource(ints = {1, 2})
    void is_rejected_when_shorter_than_the_header(int length) {
      byte[] truncated = new byte[length];
      truncated[0] = MAGIC_0;

      assertThatExceptionOfType(VersionedFormatException.class)
          .isThrownBy(() -> codec.decode(truncated))
          .withMessage("not a versioned payload: expected at least 3 bytes, got " + length);
    }

    @Test
    void is_rejected_when_the_magic_does_not_match() {
      assertThatExceptionOfType(VersionedFormatException.class)
          .isThrownBy(() -> codec.decode(new byte[] {'{', '"', 'a', '"', '}'}))
          .withMessage("not a versioned payload: bad magic");
    }

    @Test
    void is_rejected_when_only_the_first_magic_byte_matches() {
      assertThatExceptionOfType(VersionedFormatException.class)
          .isThrownBy(() -> codec.decode(new byte[] {MAGIC_0, (byte) 0xFF, 1}))
          .withMessage("not a versioned payload: bad magic");
    }

    @Test
    void is_not_reported_as_an_unknown_version() {
      assertThatExceptionOfType(VersionedFormatException.class)
          .isThrownBy(() -> codec.decode(new byte[] {0x00, 0x00, 0x01}))
          .isNotInstanceOf(UnknownVersionException.class);
    }
  }

  @Nested
  class A_version_with_no_registered_codec {

    private final Codec<String> codec =
        VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

    @Test
    void is_rejected_naming_the_version() {
      assertThatExceptionOfType(UnknownVersionException.class)
          .isThrownBy(() -> codec.decode(new byte[] {MAGIC_0, MAGIC_1, (byte) 9}))
          .satisfies(e -> assertThat(e.version()).isEqualTo(9));
    }

    @Test
    void is_rejected_for_the_reserved_zero_version() {
      assertThatExceptionOfType(UnknownVersionException.class)
          .isThrownBy(() -> codec.decode(new byte[] {MAGIC_0, MAGIC_1, 0x00}))
          .satisfies(e -> assertThat(e.version()).isZero());
    }

    @Test
    void reads_the_version_byte_as_unsigned() {
      Codec<String> wide =
          VersionedCodec.<String>builder().version(255, plain()).writing(255).build();

      byte[] encoded = wide.encode("hi");

      assertThat(encoded[2]).isEqualTo((byte) 0xFF);
      assertThat(wide.decode(encoded)).isEqualTo("hi");
    }
  }

  @Nested
  class The_builder {

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = {-1, 0, 256})
    void rejects_a_version_outside_the_valid_range(int version) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> VersionedCodec.<String>builder().version(version, plain()));
    }

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = {-1, 0, 256})
    void rejects_a_write_version_outside_the_valid_range(int version) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> VersionedCodec.<String>builder().writing(version));
    }

    @Test
    void rejects_a_null_codec() {
      assertThatNullPointerException()
          .isThrownBy(() -> VersionedCodec.<String>builder().version(1, null));
    }

    @Test
    void rejects_registering_the_same_version_twice() {
      assertThatIllegalStateException()
          .isThrownBy(
              () -> VersionedCodec.<String>builder().version(1, plain()).version(1, upperCase()));
    }

    @Test
    void rejects_building_with_no_versions_registered() {
      assertThatIllegalStateException()
          .isThrownBy(() -> VersionedCodec.<String>builder().writing(1).build())
          .withMessage("at least one version must be registered");
    }

    @Test
    void rejects_building_without_a_write_version() {
      assertThatIllegalStateException()
          .isThrownBy(() -> VersionedCodec.<String>builder().version(1, plain()).build());
    }

    @Test
    void rejects_a_write_version_with_no_registered_codec() {
      assertThatIllegalStateException()
          .isThrownBy(
              () -> VersionedCodec.<String>builder().version(1, plain()).writing(2).build());
    }

    @Test
    void lets_a_later_writing_call_replace_an_earlier_one() {
      Codec<String> codec =
          VersionedCodec.<String>builder()
              .version(1, plain())
              .version(2, upperCase())
              .writing(1)
              .writing(2)
              .build();

      byte[] encoded = codec.encode("hi");

      assertThat(encoded[2]).isEqualTo((byte) 2);
    }

    @Test
    void does_not_leak_later_registrations_into_a_built_codec() {
      VersionedCodec.Builder<String> builder =
          VersionedCodec.<String>builder().version(1, plain()).writing(1);
      Codec<String> built = builder.build();
      builder.version(2, upperCase());

      assertThatExceptionOfType(UnknownVersionException.class)
          .isThrownBy(() -> built.decode(new byte[] {MAGIC_0, MAGIC_1, 2}));
    }
  }

  @Nested
  class A_versioned_byte_transform {

    private static Codec<byte[]> reversing() {
      return new Codec<>() {
        @Override
        public byte[] encode(byte[] value) {
          byte[] out = new byte[value.length];
          for (int i = 0; i < value.length; i++) {
            out[i] = value[value.length - 1 - i];
          }
          return out;
        }

        @Override
        public byte[] decode(byte[] bytes) {
          return encode(bytes);
        }
      };
    }

    private static Codec<byte[]> identity() {
      return new Codec<>() {
        @Override
        public byte[] encode(byte[] value) {
          return value.clone();
        }

        @Override
        public byte[] decode(byte[] bytes) {
          return bytes.clone();
        }
      };
    }

    @Test
    void round_trips_inside_a_larger_chain() {
      Codec<byte[]> transform =
          VersionedCodec.<byte[]>builder()
              .version(1, identity())
              .version(2, reversing())
              .writing(2)
              .build();
      Codec<String> codec = plain().andThen(transform);

      assertThat(codec.decode(codec.encode("hello"))).isEqualTo("hello");
    }

    @Test
    void reads_a_payload_written_by_the_older_transform() {
      Codec<String> writer =
          plain()
              .andThen(VersionedCodec.<byte[]>builder().version(1, identity()).writing(1).build());
      Codec<String> reader =
          plain()
              .andThen(
                  VersionedCodec.<byte[]>builder()
                      .version(1, identity())
                      .version(2, reversing())
                      .writing(2)
                      .build());

      assertThat(reader.decode(writer.encode("hello"))).isEqualTo("hello");
    }
  }
}
