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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UnknownVersionExceptionTest {

  @Nested
  class An_unknown_version {

    @Test
    void reports_the_offending_version() {
      UnknownVersionException exception = new UnknownVersionException(7);

      assertThat(exception.version()).isEqualTo(7);
    }

    @Test
    void names_the_offending_version_in_its_message() {
      UnknownVersionException exception = new UnknownVersionException(7);

      assertThat(exception).hasMessage("unknown format version: 7");
    }

    @Test
    void is_a_versioned_format_exception() {
      assertThat(new UnknownVersionException(7)).isInstanceOf(VersionedFormatException.class);
    }
  }
}
