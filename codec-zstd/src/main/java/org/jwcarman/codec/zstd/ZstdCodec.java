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
package org.jwcarman.codec.zstd;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.jwcarman.codec.transform.compress.CompressionStreamCodec;

/**
 * Zstandard (RFC 8878) compression as a {@code Codec<byte[]>} transform, for use with {@code
 * Codec.andThen}. Faster than gzip at every level and smaller at most, which makes it the usual
 * choice for cache entries and queue payloads.
 *
 * <p>Backed by {@code zstd-jni}, which ships native libraries for the common platforms; that is why
 * this transform lives in its own module rather than {@code codec-core}.
 *
 * <p>Like the built-in compression transforms, decoding is capped: a payload that would expand
 * beyond the configured maximum is rejected before it is fully inflated, which defends against
 * decompression bombs. The default cap is 64 MiB.
 *
 * <p>Instances are immutable and thread-safe.
 */
public class ZstdCodec extends CompressionStreamCodec {

  private final int level;

  /** Creates a codec at the library's default compression level with the default decoded cap. */
  public ZstdCodec() {
    this(Zstd.defaultCompressionLevel(), DEFAULT_MAX_DECODED_SIZE);
  }

  /**
   * Creates a codec at the given compression level with the default decoded cap.
   *
   * @param level the compression level, within {@link Zstd#minCompressionLevel()} and {@link
   *     Zstd#maxCompressionLevel()}; higher is smaller and slower
   * @throws IllegalArgumentException if the level is outside the library's supported range
   */
  public ZstdCodec(int level) {
    this(level, DEFAULT_MAX_DECODED_SIZE);
  }

  /**
   * Creates a codec at the given compression level and decoded-size cap.
   *
   * @param level the compression level, within {@link Zstd#minCompressionLevel()} and {@link
   *     Zstd#maxCompressionLevel()}; higher is smaller and slower
   * @param maxDecodedSize the maximum number of bytes a payload may decode to; must be positive
   * @throws IllegalArgumentException if the level is outside the library's supported range or the
   *     cap is not positive
   */
  public ZstdCodec(int level, long maxDecodedSize) {
    int min = Zstd.minCompressionLevel();
    int max = Zstd.maxCompressionLevel();
    if (level < min || level > max) {
      throw new IllegalArgumentException(
          "level must be between " + min + " and " + max + ": " + level);
    }
    super(maxDecodedSize);
    this.level = level;
  }

  @Override
  protected OutputStream compressing(OutputStream sink) throws IOException {
    return new ZstdOutputStream(sink, level);
  }

  @Override
  protected InputStream decompressing(InputStream source) throws IOException {
    return new ZstdInputStream(source);
  }
}
