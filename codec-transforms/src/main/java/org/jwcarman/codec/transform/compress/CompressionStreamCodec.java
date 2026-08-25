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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import org.jwcarman.codec.spi.Codec;

/**
 * Base class for stream-based compression transforms, intended for use with {@link
 * Codec#andThen(Codec)}.
 *
 * <p>Subclasses supply the compressing and decompressing streams; this class owns the buffering
 * and, critically, the decompression-bomb guard: {@link #decode(byte[])} refuses to inflate past
 * the configured maximum decoded size ({@value #DEFAULT_MAX_DECODED_SIZE} bytes by default). {@code
 * encode} and {@code decode} are final so the guard cannot be bypassed.
 *
 * <p>Any stream-based compression library can be adapted by overriding the two factory methods,
 * e.g. wrapping zstd or lz4 streams from a third-party dependency.
 */
public abstract class CompressionStreamCodec implements Codec<byte[]> {

  /** Default maximum decoded size (64 MiB). */
  protected static final long DEFAULT_MAX_DECODED_SIZE = 64L * 1024 * 1024;

  private static final int BUFFER_SIZE = 8192;

  private final long maxDecodedSize;

  /**
   * Creates a codec that refuses to decode payloads expanding beyond the given size.
   *
   * @param maxDecodedSize maximum decoded size in bytes; must be positive
   * @throws IllegalArgumentException if {@code maxDecodedSize} is not positive
   */
  protected CompressionStreamCodec(long maxDecodedSize) {
    if (maxDecodedSize <= 0) {
      throw new IllegalArgumentException("maxDecodedSize must be positive: " + maxDecodedSize);
    }
    this.maxDecodedSize = maxDecodedSize;
  }

  /**
   * Wraps the sink in a stream that compresses everything written to it.
   *
   * @param sink the stream receiving compressed bytes
   * @return the compressing stream; closing it must also release any native resources it holds
   * @throws IOException if the stream cannot be created
   */
  protected abstract OutputStream compressing(OutputStream sink) throws IOException;

  /**
   * Wraps the source in a stream that decompresses everything read from it.
   *
   * @param source the stream supplying compressed bytes
   * @return the decompressing stream
   * @throws IOException if the stream cannot be created
   */
  protected abstract InputStream decompressing(InputStream source) throws IOException;

  @Override
  public final byte[] encode(byte[] value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (OutputStream compress = compressing(out)) {
      compress.write(value);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to compress data", e);
    }
    return out.toByteArray();
  }

  @Override
  public final byte[] decode(byte[] bytes) {
    try (InputStream decompress = decompressing(new ByteArrayInputStream(bytes))) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[BUFFER_SIZE];
      long total = 0;
      int read;
      while ((read = decompress.read(buffer)) != -1) {
        total += read;
        if (total > maxDecodedSize) {
          throw new IllegalStateException(
              "Decoded size exceeds the maximum of " + maxDecodedSize + " bytes");
        }
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to decompress data", e);
    }
  }
}
