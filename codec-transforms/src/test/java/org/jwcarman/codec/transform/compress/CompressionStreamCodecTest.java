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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.InvalidPayloadException;
import org.jwcarman.codec.spi.TransientCodecException;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompressionStreamCodecTest {

  private static final class ThrowingStreamCodec extends CompressionStreamCodec {

    private ThrowingStreamCodec() {
      this(1024);
    }

    private ThrowingStreamCodec(long maxDecodedSize) {
      super(maxDecodedSize);
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
  void encode_reports_a_failing_compressor_as_transient() {
    assertThatExceptionOfType(TransientCodecException.class)
        .isThrownBy(() -> codec.encode(new byte[] {1, 2, 3}))
        .withMessage("Unable to compress data")
        .withCauseInstanceOf(IOException.class);
  }

  @Test
  void decode_reports_a_corrupt_stream_as_an_invalid_payload() {
    assertThatExceptionOfType(InvalidPayloadException.class)
        .isThrownBy(() -> codec.decode(new byte[] {1, 2, 3}))
        .withMessage("Unable to decompress data")
        .withCauseInstanceOf(IOException.class);
  }

  @Test
  void accepts_a_cap_larger_than_a_single_array_can_hold() {
    // Long.MAX_VALUE is the idiom for "no cap"; it is clamped to the array ceiling rather than
    // rejected, so that past it the codec fails with the documented InvalidPayloadException.
    assertThat(new ThrowingStreamCodec(Long.MAX_VALUE)).isNotNull();
  }

  @Test
  void encode_rejects_null_before_touching_the_stream() {
    assertThatNullPointerException()
        .isThrownBy(() -> codec.encode(null))
        .withMessage("value must not be null");
  }

  @Test
  void decode_rejects_null_before_touching_the_stream() {
    assertThatNullPointerException()
        .isThrownBy(() -> codec.decode(null))
        .withMessage("bytes must not be null");
  }
}
