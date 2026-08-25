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
package org.jwcarman.codec.transform.compress;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompressionStreamCodecTest {

  private static final class ThrowingStreamCodec extends CompressionStreamCodec {

    private ThrowingStreamCodec() {
      super(1024);
    }

    @Override
    protected OutputStream compressing(OutputStream sink) {
      return new OutputStream() {
        @Override
        public void write(int b) throws IOException {
          throw new IOException("boom");
        }
      };
    }

    @Override
    protected InputStream decompressing(InputStream source) {
      return new InputStream() {
        @Override
        public int read() throws IOException {
          throw new IOException("boom");
        }
      };
    }
  }

  private final ThrowingStreamCodec codec = new ThrowingStreamCodec();

  @Test
  void encode_wraps_io_failures_in_unchecked_io_exception() {
    assertThatExceptionOfType(UncheckedIOException.class)
        .isThrownBy(() -> codec.encode(new byte[] {1, 2, 3}));
  }

  @Test
  void decode_wraps_io_failures_in_unchecked_io_exception() {
    assertThatExceptionOfType(UncheckedIOException.class)
        .isThrownBy(() -> codec.decode(new byte[] {1, 2, 3}));
  }
}
