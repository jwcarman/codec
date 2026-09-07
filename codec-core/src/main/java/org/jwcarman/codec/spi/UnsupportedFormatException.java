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
 * The payload handed to {@link Codec#decode} is well-formed, but names a format this reader cannot
 * handle: a version or an algorithm identifier this instance was not built to understand —
 * typically because a newer writer produced it.
 *
 * <p>The data is not bad and nothing is down, so the caller's response is to hold the payload,
 * route it to a reader that understands it, or upgrade — never to quarantine it. Distinguishing
 * this from {@link InvalidPayloadException} is what makes a rolling upgrade safe: a policy that
 * dead-letters invalid payloads must not discard everything written by the instances that have
 * already been upgraded.
 */
public class UnsupportedFormatException extends CodecException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message which version or algorithm was not recognised
   */
  public UnsupportedFormatException(String message) {
    super(message);
  }

  /**
   * Creates the exception with an underlying cause.
   *
   * @param message which version or algorithm was not recognised
   * @param cause the underlying exception
   */
  public UnsupportedFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
