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
package org.jwcarman.codec.versioned;

import org.jwcarman.codec.spi.InvalidPayloadException;

/**
 * Signals that a buffer handed to a versioned codec is not a well-formed versioned payload: it is
 * shorter than the three-byte header, or it does not carry the versioned magic.
 *
 * <p>This is the "these bytes were never ours" failure — a codec pointed at data some other codec
 * wrote — and so an {@link InvalidPayloadException}: quarantine it. Contrast {@link
 * UnknownVersionException}, which means the framing is ours but the version is one this codec does
 * not know; that is an {@link org.jwcarman.codec.spi.UnsupportedFormatException}, and the two
 * deliberately share no parent below {@link org.jwcarman.codec.spi.CodecException}.
 */
public class VersionedFormatException extends InvalidPayloadException {

  /**
   * Creates an exception with the given message.
   *
   * @param message the detail message
   */
  public VersionedFormatException(String message) {
    super(message);
  }
}
