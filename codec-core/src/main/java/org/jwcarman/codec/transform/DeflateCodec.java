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
package org.jwcarman.codec.transform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Compresses with the zlib format (RFC 1950): DEFLATE data wrapped in a two-byte header and
 * Adler-32 checksum.
 *
 * <p>Same algorithm as {@link GzipCodec} with roughly twelve bytes less framing per payload —
 * useful for high volumes of small messages. Prefer {@link GzipCodec} when the bytes must
 * interoperate with external gzip tooling.
 */
public class DeflateCodec extends CompressionStreamCodec {

  private final int level;

  /** Creates a codec with the default compression level and decoded-size cap of 64 MiB. */
  public DeflateCodec() {
    this(Deflater.DEFAULT_COMPRESSION, DEFAULT_MAX_DECODED_SIZE);
  }

  /**
   * Creates a codec with the default compression level and a custom decoded-size cap.
   *
   * @param maxDecodedSize maximum decoded size in bytes; must be positive
   * @throws IllegalArgumentException if {@code maxDecodedSize} is not positive
   */
  public DeflateCodec(long maxDecodedSize) {
    this(Deflater.DEFAULT_COMPRESSION, maxDecodedSize);
  }

  /**
   * Creates a codec with a specific compression level and decoded-size cap.
   *
   * @param level compression level: {@link Deflater#DEFAULT_COMPRESSION} (-1), or 0 (stored) to 9
   *     (best compression)
   * @param maxDecodedSize maximum decoded size in bytes; must be positive
   * @throws IllegalArgumentException if {@code level} is outside -1..9 or {@code maxDecodedSize} is
   *     not positive
   */
  public DeflateCodec(int level, long maxDecodedSize) {
    if (level < Deflater.DEFAULT_COMPRESSION || level > Deflater.BEST_COMPRESSION) {
      throw new IllegalArgumentException("level must be between -1 and 9: " + level);
    }
    super(maxDecodedSize);
    this.level = level;
  }

  @Override
  protected OutputStream compressing(OutputStream sink) throws IOException {
    return new LeveledDeflaterOutputStream(sink, level);
  }

  @Override
  protected InputStream decompressing(InputStream source) throws IOException {
    return new InflaterInputStream(source);
  }

  private static final class LeveledDeflaterOutputStream extends DeflaterOutputStream {

    private LeveledDeflaterOutputStream(OutputStream sink, int level) {
      super(sink);
      def.setLevel(level);
    }
  }
}
