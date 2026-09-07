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

/**
 * Something the codec depends on failed, and the input is not at fault: a key-management service
 * timed out or throttled, a JCE provider cannot supply a transform, a compressor failed on valid
 * input, or a provider returned something that violates its contract.
 *
 * <p>The caller's response is to retry with backoff, or to alert on infrastructure — never to blame
 * or discard the input. The name follows the precedent of Spring's {@code
 * TransientDataAccessException}; it slightly overclaims for a misconfigured dependency, which is
 * not transient, but the caller's response is the same and that is what the family is for. The
 * underlying failure is preserved as the cause whenever there is one.
 */
public class TransientCodecException extends CodecException {

  /**
   * Creates the exception.
   *
   * @param message what dependency failed
   */
  public TransientCodecException(String message) {
    super(message);
  }

  /**
   * Creates the exception with the dependency's failure as the cause.
   *
   * @param message what dependency failed
   * @param cause the dependency's own exception
   */
  public TransientCodecException(String message, Throwable cause) {
    super(message, cause);
  }
}
