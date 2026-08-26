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

import java.security.Provider;

/** Shared helpers for reporting on an optional JCA {@link Provider}. */
final class Providers {

  private Providers() {}

  /**
   * Names a provider for an error message.
   *
   * @param provider the provider, or {@code null} for the JDK's default lookup
   * @return the provider's name, or {@code <default>} when none was set
   */
  static String describe(Provider provider) {
    return provider == null ? "<default>" : provider.getName();
  }
}
