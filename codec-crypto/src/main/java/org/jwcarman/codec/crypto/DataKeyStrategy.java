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

/**
 * Pure lifecycle policy for acquiring the data-encryption key used to encode a message.
 *
 * <p>A strategy owns no key material itself; it decides, given a {@link DataKeyProvider}, whether
 * to request a fresh data key or reuse one already held. Implementations MUST be safe for
 * concurrent use.
 */
public interface DataKeyStrategy {

  /**
   * Acquires the data key to use for the next message, consulting the given provider as needed.
   *
   * @param provider the provider to acquire a fresh data key from, if required
   * @return the {@link DataKey} to use
   */
  DataKey acquire(DataKeyProvider provider);
}
