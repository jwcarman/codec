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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A byte transform that can wrap a stream, so a chain built with {@link Codec#andThen} moves bytes
 * through it as they are produced instead of collecting them between stages. A transform that is
 * naturally stream-shaped (compression, Base64) implements the two wrapper methods and inherits
 * array and stream {@code encode}/{@code decode} from them; a transform that must see the whole
 * payload (an AEAD cipher, a trailer checksum) stays a plain {@code Codec<byte[]>} and buffers.
 */
public interface StreamingTransform extends Codec<byte[]> {

  /**
   * Wraps {@code sink}: bytes written to the returned stream reach {@code sink} transformed.
   * Closing the returned stream finishes the transform's framing; it may close {@code sink}, so a
   * caller that keeps {@code sink} open shields it with {@link Streams#nonClosing(OutputStream)}.
   *
   * @param sink the stream receiving transformed bytes
   * @return the wrapping stream
   * @throws IOException if the wrapper cannot be created
   */
  OutputStream encoding(OutputStream sink) throws IOException;

  /**
   * Wraps {@code source}: bytes read from the returned stream are {@code source}'s, inverted.
   *
   * @param source the stream supplying transformed bytes
   * @return the unwrapping stream
   * @throws IOException if the wrapper cannot be created
   */
  InputStream decoding(InputStream source) throws IOException;

  @Override
  default void encodeTo(byte[] value, OutputStream out) throws IOException {
    try (OutputStream encoding = encoding(Streams.nonClosing(out))) {
      encoding.write(value);
    }
  }

  @Override
  default byte[] decodeFrom(InputStream in) throws IOException {
    try (InputStream decoding = decoding(Streams.nonClosing(in))) {
      return decoding.readAllBytes();
    }
  }
}
