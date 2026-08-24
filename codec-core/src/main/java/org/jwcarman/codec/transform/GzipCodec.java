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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jwcarman.codec.spi.Codec;

public class GzipCodec implements Codec<byte[]> {

  private static final long DEFAULT_MAX_DECODED_SIZE = 64L * 1024 * 1024;

  private final long maxDecodedSize;

  public GzipCodec() {
    this(DEFAULT_MAX_DECODED_SIZE);
  }

  public GzipCodec(long maxDecodedSize) {
    if (maxDecodedSize <= 0) {
      throw new IllegalArgumentException("maxDecodedSize must be positive: " + maxDecodedSize);
    }
    this.maxDecodedSize = maxDecodedSize;
  }

  @Override
  public byte[] encode(byte[] value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(value);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to gzip data", e);
    }
    return out.toByteArray();
  }

  @Override
  public byte[] decode(byte[] bytes) {
    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = gzip.read(buffer)) != -1) {
        total += read;
        if (total > maxDecodedSize) {
          throw new IllegalStateException(
              "Decoded size exceeds the maximum of " + maxDecodedSize + " bytes");
        }
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to gunzip data", e);
    }
  }
}
