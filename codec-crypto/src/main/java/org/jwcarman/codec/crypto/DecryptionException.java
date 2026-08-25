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
 * Exception indicating that the data provided for decryption is invalid or corrupted.
 *
 * <p>This exception signals that the encrypted payload was tampered with, contains invalid data, or
 * is fundamentally incompatible with the decryption operation. The message and cause provide
 * diagnostic details about why decryption failed.
 *
 * <p>Note on timing side-channels: the indistinguishability of different decryption failures is
 * determined solely by this exception's message content and not by the timing of its throw.
 */
public class DecryptionException extends IllegalArgumentException {

  private static final String CRYPTOGRAPHIC_FAILURE = "Unable to decrypt data";

  public DecryptionException(String message) {
    super(message);
  }

  public DecryptionException(String message, Throwable cause) {
    super(message, cause);
  }

  public static DecryptionException cryptographic(Throwable cause) {
    return new DecryptionException(CRYPTOGRAPHIC_FAILURE, cause);
  }
}
