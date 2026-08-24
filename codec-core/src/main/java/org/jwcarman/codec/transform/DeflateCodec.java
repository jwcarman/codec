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

public class DeflateCodec extends CompressionStreamCodec {

  private final int level;

  public DeflateCodec() {
    this(Deflater.DEFAULT_COMPRESSION, DEFAULT_MAX_DECODED_SIZE);
  }

  public DeflateCodec(long maxDecodedSize) {
    this(Deflater.DEFAULT_COMPRESSION, maxDecodedSize);
  }

  public DeflateCodec(int level, long maxDecodedSize) {
    if (level < Deflater.DEFAULT_COMPRESSION || level > Deflater.BEST_COMPRESSION) {
      throw new IllegalArgumentException("level must be between -1 and 9: " + level);
    }
    super(maxDecodedSize);
    this.level = level;
  }

  @Override
  protected OutputStream compressing(OutputStream sink) throws IOException {
    return new DeflaterOutputStream(sink, new Deflater(level)) {
      @Override
      public void close() throws IOException {
        try {
          super.close();
        } finally {
          def.end();
        }
      }
    };
  }

  @Override
  protected InputStream decompressing(InputStream source) throws IOException {
    return new InflaterInputStream(source);
  }
}
