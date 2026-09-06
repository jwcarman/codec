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
 * <p>Scope of the indistinguishability guarantee: every cryptographic rejection shares the exact
 * same <em>message</em>. The <em>cause</em> is preserved for diagnosis and does differ by stage —
 * an AES-KW integrity failure carries the JCE exception from the unwrap, a GCM tag mismatch carries
 * {@code AEADBadTagException}, an unknown KEK id carries none — so a consumer that exposes stack
 * traces exposes which stage rejected the input. Log the message, not the trace, where that
 * distinction must not leak. The guarantee does not cover timing either. The timing side channel is
 * unavoidable and explicitly out of scope: unwrapping a key through a remote provider and verifying
 * a GCM tag locally take observably different amounts of time, and structural rejections (bad
 * magic, unknown version, an out-of-bounds length) return before any provider call is even made.
 * Callers who need timing-independent behavior across all rejection categories must build that at a
 * layer above this exception's message.
 */
public class DecryptionException extends IllegalArgumentException {

  private static final String CRYPTOGRAPHIC_FAILURE = "Unable to decrypt data";

  /**
   * Creates a structural rejection with a stage-specific message.
   *
   * @param message what the decoder rejected; must not reveal key material
   */
  public DecryptionException(String message) {
    super(message);
  }

  /**
   * Creates a rejection carrying the underlying cause.
   *
   * @param message what the decoder rejected; must not reveal key material
   * @param cause the underlying failure, may be {@code null}
   */
  public DecryptionException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a cryptographic rejection with the uniform message shared by every tag, unwrap and
   * scheme failure, so that callers cannot distinguish them by content.
   *
   * @param cause the underlying failure, may be {@code null}
   * @return the exception to throw
   */
  public static DecryptionException cryptographic(Throwable cause) {
    return new DecryptionException(CRYPTOGRAPHIC_FAILURE, cause);
  }
}
