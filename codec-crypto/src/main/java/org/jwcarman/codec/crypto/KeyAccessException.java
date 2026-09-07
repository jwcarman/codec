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

import org.jwcarman.codec.spi.TransientCodecException;

/**
 * Exception indicating that key infrastructure is unavailable.
 *
 * <p>This exception signals that the encryption or decryption operation could not proceed because
 * the key management system or key provider is not accessible. This is a transient state failure —
 * the operation may succeed if retried later. It is a {@link TransientCodecException}: retry, or
 * alert on key infrastructure; never quarantine the data.
 *
 * <p>Important: Never quarantine the encrypted data when this exception occurs. The inability to
 * access keys is a temporary infrastructure issue, not a data validation failure. Encrypted data
 * should be retained for retry.
 */
public class KeyAccessException extends TransientCodecException {

  /**
   * Creates a key-infrastructure availability failure.
   *
   * @param message what was unavailable
   * @param cause the provider's failure, preserved for diagnosis
   */
  public KeyAccessException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a key-infrastructure failure that has no underlying exception: the provider returned
   * normally, but what it returned violates the {@link DataKeyProvider} contract.
   *
   * @param message what the provider got wrong
   */
  public KeyAccessException(String message) {
    super(message);
  }
}
