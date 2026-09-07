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
 * The payload handed to {@link Codec#decode} is malformed, corrupt, or forged: bad framing,
 * truncation, an authentication-tag or checksum mismatch, text outside an encoding's alphabet, a
 * corrupt compressed stream, a payload that would expand past a size cap, or one that decodes to a
 * type this codec does not produce.
 *
 * <p>The caller's response is to quarantine the payload — dead-letter it, treat the cache entry as
 * a miss, reject the request — and never to retry it as is. The underlying library's exception,
 * when there is one, is preserved as the cause.
 */
public class InvalidPayloadException extends CodecException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what about the payload was rejected, never including its contents
   */
  public InvalidPayloadException(String message) {
    super(message);
  }

  /**
   * Creates the exception with the underlying failure as the cause.
   *
   * @param message what about the payload was rejected, never including its contents
   * @param cause the underlying exception
   */
  public InvalidPayloadException(String message, Throwable cause) {
    super(message, cause);
  }
}
