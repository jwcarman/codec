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
package org.jwcarman.codec.lz4;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.jpountz.xxhash.XXHashFactory;
import org.jwcarman.codec.transform.compress.CompressionStreamCodec;

/**
 * LZ4 compression as a {@code Codec<byte[]>} transform, for use with {@code Codec.andThen}. LZ4
 * trades ratio for speed — it compresses and decompresses at memory-bandwidth rates — so it suits
 * hot caches and high-volume message streams where the payload must be small enough, not as small
 * as possible. When ratio matters more than raw speed, prefer {@code ZstdCodec}.
 *
 * <p>Output is the standard LZ4 <em>frame</em> format (magic {@code 04 22 4D 18}), the same framing
 * the {@code lz4} command-line tool, Kafka, and other LZ4 producers use, so payloads interoperate.
 * Frames carry a content checksum, which catches corruption on decode.
 *
 * <p>The default compressor is the fast one; {@link #highCompression()} selects LZ4-HC, which
 * compresses several times slower for a noticeably better ratio while decompressing at the same
 * speed. Backed by {@code lz4-java}, which ships native libraries for the common platforms and
 * falls back to pure Java elsewhere; that dependency is why this transform lives in its own module
 * rather than {@code codec-transforms}.
 *
 * <p>Like the built-in compression transforms, decoding is capped: a payload that would expand
 * beyond the configured maximum is rejected before it is fully inflated, which defends against
 * decompression bombs. The default cap is 64 MiB.
 *
 * <p>Instances are immutable and thread-safe.
 */
public class Lz4Codec extends CompressionStreamCodec {

  private static final LZ4Factory FACTORY = LZ4Factory.fastestInstance();
  private static final XXHashFactory HASH_FACTORY = XXHashFactory.fastestInstance();

  private final LZ4Compressor compressor;

  /** Creates a codec using the fast compressor with the default decoded cap. */
  public Lz4Codec() {
    this(DEFAULT_MAX_DECODED_SIZE);
  }

  /**
   * Creates a codec using the fast compressor with the given decoded-size cap.
   *
   * @param maxDecodedSize the maximum number of bytes a payload may decode to; must be positive
   * @throws IllegalArgumentException if the cap is not positive
   */
  public Lz4Codec(long maxDecodedSize) {
    this(FACTORY.fastCompressor(), maxDecodedSize);
  }

  private Lz4Codec(LZ4Compressor compressor, long maxDecodedSize) {
    super(maxDecodedSize);
    this.compressor = compressor;
  }

  /**
   * Creates a codec using the LZ4-HC compressor with the default decoded cap: a better ratio at a
   * slower compression speed, with decompression as fast as ever.
   *
   * @return a high-compression codec
   */
  public static Lz4Codec highCompression() {
    return highCompression(DEFAULT_MAX_DECODED_SIZE);
  }

  /**
   * Creates a codec using the LZ4-HC compressor with the given decoded-size cap.
   *
   * @param maxDecodedSize the maximum number of bytes a payload may decode to; must be positive
   * @return a high-compression codec
   * @throws IllegalArgumentException if the cap is not positive
   */
  public static Lz4Codec highCompression(long maxDecodedSize) {
    return new Lz4Codec(FACTORY.highCompressor(), maxDecodedSize);
  }

  @Override
  protected OutputStream compressing(OutputStream sink) throws IOException {
    return new LZ4FrameOutputStream(
        sink,
        LZ4FrameOutputStream.BLOCKSIZE.SIZE_64KB,
        -1L,
        compressor,
        HASH_FACTORY.hash32(),
        LZ4FrameOutputStream.FLG.Bits.BLOCK_INDEPENDENCE,
        LZ4FrameOutputStream.FLG.Bits.CONTENT_CHECKSUM);
  }

  @Override
  protected InputStream decompressing(InputStream source) throws IOException {
    return new LZ4FrameInputStream(source);
  }
}
