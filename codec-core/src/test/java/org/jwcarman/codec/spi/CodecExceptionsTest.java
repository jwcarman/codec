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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CodecExceptionsTest {

  /** One family: how to build it with and without a cause. */
  private record Family(
      Class<? extends CodecException> type,
      Function<String, CodecException> withMessage,
      BiFunction<String, Throwable, CodecException> withCause) {}

  private static final List<Family> FAMILIES =
      List.of(
          new Family(
              InvalidValueException.class, InvalidValueException::new, InvalidValueException::new),
          new Family(
              InvalidPayloadException.class,
              InvalidPayloadException::new,
              InvalidPayloadException::new),
          new Family(
              UnsupportedFormatException.class,
              UnsupportedFormatException::new,
              UnsupportedFormatException::new),
          new Family(
              TransientCodecException.class,
              TransientCodecException::new,
              TransientCodecException::new));

  @Nested
  class Every_family {

    @Test
    void is_an_unchecked_codec_exception() {
      for (Family family : FAMILIES) {
        CodecException instance = family.withMessage().apply("m");
        assertThat(instance)
            .isInstanceOf(CodecException.class)
            .isInstanceOf(RuntimeException.class);
        // The families extend CodecException directly and nothing else.
        assertThat(instance.getClass().getSuperclass()).isEqualTo(CodecException.class);
      }
    }

    @Test
    void preserves_its_message_and_cause() {
      Throwable cause = new IllegalStateException("underlying");
      for (Family family : FAMILIES) {
        assertThat(family.withMessage().apply("just a message"))
            .hasMessage("just a message")
            .hasNoCause();
        assertThat(family.withCause().apply("with a cause", cause))
            .hasMessage("with a cause")
            .hasCause(cause);
      }
    }

    @Test
    void is_not_a_subtype_of_any_other_family() {
      // The families are siblings keyed to different caller responses; a catch for one must
      // never accidentally take another.
      for (Family family : FAMILIES) {
        CodecException instance = family.withMessage().apply("m");
        for (Family other : FAMILIES) {
          if (other != family) {
            assertThat(instance).isNotInstanceOf(other.type());
          }
        }
      }
    }
  }
}
