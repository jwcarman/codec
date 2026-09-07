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

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stream shields for composed codecs: a stage's wrapper (a {@code GZIPOutputStream}, a Base64
 * encoder) must be closed to finish its frame, but the caller's stream is the caller's to close.
 */
public final class Streams {

  private Streams() {}

  /**
   * A view of {@code sink} whose {@code close()} flushes instead of closing.
   *
   * @param sink the caller's stream
   * @return a stream that forwards writes and never closes {@code sink}
   */
  public static OutputStream nonClosing(OutputStream sink) {
    return new FilterOutputStream(sink) {
      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
      }

      @Override
      public void close() throws IOException {
        out.flush();
      }
    };
  }

  /**
   * A view of {@code source} whose {@code close()} does nothing.
   *
   * @param source the caller's stream
   * @return a stream that forwards reads and never closes {@code source}
   */
  public static InputStream nonClosing(InputStream source) {
    return new FilterInputStream(source) {
      @Override
      public void close() {
        // the caller owns the source
      }
    };
  }
}
