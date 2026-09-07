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
 * The root of every failure a {@link Codec} reports from {@link Codec#encode} or {@link
 * Codec#decode}.
 *
 * <p>This class is never thrown directly; it exists for the coarse {@code catch (CodecException e)}
 * — "something went wrong in the codec". Its four subclasses are keyed to what the caller does
 * next, and a catch for one of them is the precise form:
 *
 * <ul>
 *   <li>{@link InvalidValueException} — the value handed to {@code encode} cannot be encoded: fix
 *       the value or the configuration.
 *   <li>{@link InvalidPayloadException} — the payload handed to {@code decode} is malformed,
 *       corrupt, or forged: quarantine it.
 *   <li>{@link UnsupportedFormatException} — the payload is well-formed but this reader cannot
 *       handle it (a format version or algorithm it does not know): hold it, route it to a newer
 *       reader, or upgrade.
 *   <li>{@link TransientCodecException} — something the codec depends on failed; the input is not
 *       at fault: retry, or alert on infrastructure.
 * </ul>
 *
 * <p>The rule for extending the hierarchy: subclass by what the caller does next. A failure with
 * one of the four answers above is a subclass of that family, never a fifth sibling. A more
 * specific subclass earns its existence only when it carries information the family does not.
 *
 * <p>Construction and configuration errors — a bad builder argument, a null mapper, a type a
 * factory cannot create a codec for, a {@code null} passed to {@code encode} or {@code decode} —
 * are programmer errors at wiring time and remain {@link IllegalArgumentException}, {@link
 * IllegalStateException} and {@link NullPointerException}; they are not codec failures.
 */
public class CodecException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a codec failure with the given message; for subclasses.
   *
   * @param message what went wrong, in plain words, never containing payload contents
   */
  protected CodecException(String message) {
    super(message);
  }

  /**
   * Creates a codec failure with the given message and cause; for subclasses.
   *
   * @param message what went wrong, in plain words, never containing payload contents
   * @param cause the underlying exception, typically the wrapped library's own
   */
  protected CodecException(String message, Throwable cause) {
    super(message, cause);
  }
}
