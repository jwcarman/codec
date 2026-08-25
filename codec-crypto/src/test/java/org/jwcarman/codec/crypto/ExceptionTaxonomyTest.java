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
package org.jwcarman.codec.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExceptionTaxonomyTest {

  @Test
  void decryption_exception_is_an_illegal_argument_exception() {
    assertThat(new DecryptionException("bad magic")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cryptographic_failures_share_one_uniform_message() {
    var cause = new RuntimeException("tag mismatch detail");
    DecryptionException e = DecryptionException.cryptographic(cause);
    assertThat(e).hasMessage("Unable to decrypt data").hasCause(cause);
  }

  @Test
  void key_access_exception_is_an_illegal_state_exception_preserving_cause() {
    var cause = new RuntimeException("kms timeout");
    assertThat(new KeyAccessException("key infrastructure unavailable", cause))
        .isInstanceOf(IllegalStateException.class)
        .hasCause(cause);
  }

  @Test
  void encryption_exception_is_an_illegal_state_exception_preserving_cause() {
    var cause = new RuntimeException("provider down");
    assertThat(new EncryptionException("unable to encrypt", cause))
        .isInstanceOf(IllegalStateException.class)
        .hasCause(cause);
  }
}
