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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compresses with the gzip format (RFC 1952): DEFLATE data wrapped in a gzip header and CRC-32
 * trailer.
 *
 * <p>Prefer this over {@link DeflateCodec} when the bytes must interoperate with external gzip
 * tooling; prefer {@link DeflateCodec} to save roughly twelve bytes of framing per payload.
 */
public class GzipCodec extends CompressionStreamCodec {

  /** Creates a codec with the default decoded-size cap of 64 MiB. */
  public GzipCodec() {
    super(DEFAULT_MAX_DECODED_SIZE);
  }

  /**
   * Creates a codec with a custom decoded-size cap.
   *
   * @param maxDecodedSize maximum decoded size in bytes; must be positive
   * @throws IllegalArgumentException if {@code maxDecodedSize} is not positive
   */
  public GzipCodec(long maxDecodedSize) {
    super(maxDecodedSize);
  }

  @Override
  protected OutputStream compressing(OutputStream sink) throws IOException {
    return new GZIPOutputStream(sink);
  }

  @Override
  protected InputStream decompressing(InputStream source) throws IOException {
    return new GZIPInputStream(source);
  }
}
