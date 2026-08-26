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
package org.jwcarman.codec.transform.checksum;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;
import org.jwcarman.codec.spi.Codec;

/**
 * A {@code Codec<byte[]>} transform that appends a 32-bit checksum (big-endian) on encode and
 * verifies it on decode, rejecting a mismatch with {@link IllegalArgumentException}. It detects
 * accidental corruption — bit rot, a truncated write, a partially overwritten cache entry — so that
 * damaged bytes fail here instead of confusing a parser downstream or decoding to a plausible but
 * wrong value.
 *
 * <p>It is <em>not</em> tamper-proof: anyone who can change the bytes can recompute the checksum.
 * Encrypted payloads from {@code codec-crypto} are already authenticated and gain nothing from
 * this; gzip, Zstandard and LZ4 frames carry their own checksums. The realistic use is an
 * uncompressed, unencrypted payload such as plain JSON in a cache.
 *
 * <p>{@link #crc32c()} is the recommended form: CRC-32C has better error-detection properties than
 * CRC-32 or Adler-32 and a dedicated CPU instruction on common hardware. Any other 32-bit {@link
 * Checksum} — {@code CRC32}, {@code Adler32}, or a custom one — can be supplied for
 * interoperability. {@link Checksum#getValue()} returns a {@code long} only because Java has no
 * unsigned {@code int}; a checksum whose value does not fit in 32 bits is rejected rather than
 * truncated.
 *
 * <p>Instances are immutable and thread-safe; a fresh {@link Checksum} is taken from the supplier
 * per operation.
 */
public final class ChecksumCodec implements Codec<byte[]> {

  private static final int CHECKSUM_LENGTH = 4;

  private final Supplier<? extends Checksum> checksums;

  /**
   * Creates a codec over any 32-bit {@link Checksum}.
   *
   * @param checksums supplies a fresh checksum per operation
   * @throws NullPointerException if {@code checksums} is null
   */
  public ChecksumCodec(Supplier<? extends Checksum> checksums) {
    this.checksums = Objects.requireNonNull(checksums, "checksums must not be null");
  }

  /**
   * CRC-32C (Castagnoli), the recommended checksum.
   *
   * @return a CRC-32C codec
   */
  public static ChecksumCodec crc32c() {
    return new ChecksumCodec(CRC32C::new);
  }

  @Override
  public byte[] encode(byte[] value) {
    Objects.requireNonNull(value, "value must not be null");
    byte[] result = Arrays.copyOf(value, value.length + CHECKSUM_LENGTH);
    int checksum = checksumOf(value, value.length);
    result[value.length] = (byte) (checksum >>> 24);
    result[value.length + 1] = (byte) (checksum >>> 16);
    result[value.length + 2] = (byte) (checksum >>> 8);
    result[value.length + 3] = (byte) checksum;
    return result;
  }

  @Override
  public byte[] decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    if (bytes.length < CHECKSUM_LENGTH) {
      throw new IllegalArgumentException("Input is shorter than a checksum: " + bytes.length);
    }
    int payloadLength = bytes.length - CHECKSUM_LENGTH;
    int expected =
        ((bytes[payloadLength] & 0xFF) << 24)
            | ((bytes[payloadLength + 1] & 0xFF) << 16)
            | ((bytes[payloadLength + 2] & 0xFF) << 8)
            | (bytes[payloadLength + 3] & 0xFF);
    int actual = checksumOf(bytes, payloadLength);
    if (actual != expected) {
      throw new IllegalArgumentException("Checksum mismatch: payload is corrupt");
    }
    return Arrays.copyOf(bytes, payloadLength);
  }

  private int checksumOf(byte[] bytes, int length) {
    Checksum checksum = checksums.get();
    checksum.update(bytes, 0, length);
    long value = checksum.getValue();
    if ((value >>> Integer.SIZE) != 0) {
      throw new IllegalStateException(
          checksum.getClass().getName() + " produced a value wider than 32 bits");
    }
    return (int) value;
  }
}
