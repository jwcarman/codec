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
 * The value handed to {@link Codec#encode} cannot be encoded: a type the backend has no serializer
 * for, a cyclic object graph, a class the backend requires to be registered and is not.
 *
 * <p>The caller's response is to fix the value or the codec's configuration; retrying will not help
 * and the codec itself is healthy. The backend's own exception is preserved as the cause.
 */
public class InvalidValueException extends CodecException {

  /**
   * Creates the exception.
   *
   * @param message what about the value could not be encoded
   */
  public InvalidValueException(String message) {
    super(message);
  }

  /**
   * Creates the exception with the backend's failure as the cause.
   *
   * @param message what about the value could not be encoded
   * @param cause the backend's own exception
   */
  public InvalidValueException(String message, Throwable cause) {
    super(message, cause);
  }
}
