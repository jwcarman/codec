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
 * Exception indicating a failure during the encryption operation.
 *
 * <p>This exception signals that the encryption process could not complete successfully. This may
 * occur due to cryptographic provider failures, state violations, or other encode-side issues. The
 * message and cause provide diagnostic details about the underlying failure.
 */
public class EncryptionException extends IllegalStateException {

  public EncryptionException(String message, Throwable cause) {
    super(message, cause);
  }
}
