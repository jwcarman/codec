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
package org.jwcarman.codec.transform.encoding;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import org.jwcarman.codec.spi.Codec;

/**
 * A byte-to-text-safe-byte transform using Base32 (RFC 4648 §6) or its "extended hex" variant (RFC
 * 4648 §7). Base32 is larger than Base64 (8 characters per 5 bytes) but its alphabet has no
 * lower-case letters and no symbols, which makes it safe for case-insensitive contexts — DNS
 * labels, file names, and values a person will read aloud or type. It is the encoding used for TOTP
 * secrets.
 *
 * <p>Output is upper-case with {@code =} padding, exactly as the RFC specifies. Decoding is strict
 * but case-insensitive: a character outside the alphabet, misplaced padding, or a length that is
 * not a multiple of eight is rejected with {@link IllegalArgumentException}.
 *
 * <p>Like {@link Base64Codec}, put it <em>last</em> in a chain. Instances are immutable and
 * thread-safe.
 */
public final class Base32Codec implements Codec<byte[]> {

  private static final String STANDARD_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final String HEX_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUV";
  private static final char PAD = '=';
  private static final int BITS_PER_CHAR = 5;
  private static final int CHARS_PER_GROUP = 8;
  private static final int BYTES_PER_GROUP = 5;

  private final char[] alphabet;
  private final byte[] lookup;

  private Base32Codec(String alphabet) {
    this.alphabet = alphabet.toCharArray();
    this.lookup = new byte[128];
    Arrays.fill(lookup, (byte) -1);
    for (int i = 0; i < this.alphabet.length; i++) {
      lookup[this.alphabet[i]] = (byte) i;
      lookup[Character.toLowerCase(this.alphabet[i])] = (byte) i;
    }
  }

  /**
   * The standard alphabet ({@code A-Z2-7}), used by TOTP secrets and most Base32 consumers.
   *
   * @return a codec for RFC 4648 §6 Base32
   */
  public static Base32Codec standard() {
    return new Base32Codec(STANDARD_ALPHABET);
  }

  /**
   * The "extended hex" alphabet ({@code 0-9A-V}), whose encoded form sorts in the same order as the
   * bytes it encodes — useful for sortable keys.
   *
   * @return a codec for RFC 4648 §7 base32hex
   */
  public static Base32Codec hex() {
    return new Base32Codec(HEX_ALPHABET);
  }

  @Override
  public byte[] encode(byte[] value) {
    Objects.requireNonNull(value, "value must not be null");
    StringBuilder out =
        new StringBuilder((value.length + BYTES_PER_GROUP - 1) / BYTES_PER_GROUP * CHARS_PER_GROUP);
    int buffer = 0;
    int bitsInBuffer = 0;
    for (byte b : value) {
      buffer = (buffer << 8) | (b & 0xFF);
      bitsInBuffer += 8;
      while (bitsInBuffer >= BITS_PER_CHAR) {
        bitsInBuffer -= BITS_PER_CHAR;
        out.append(alphabet[(buffer >> bitsInBuffer) & 0x1F]);
      }
    }
    if (bitsInBuffer > 0) {
      out.append(alphabet[(buffer << (BITS_PER_CHAR - bitsInBuffer)) & 0x1F]);
    }
    while (out.length() % CHARS_PER_GROUP != 0) {
      out.append(PAD);
    }
    return out.toString().getBytes(StandardCharsets.US_ASCII);
  }

  @Override
  public byte[] decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    if (bytes.length % CHARS_PER_GROUP != 0) {
      throw new IllegalArgumentException(
          "Base32 input length must be a multiple of " + CHARS_PER_GROUP + ": " + bytes.length);
    }
    int end = bytes.length;
    while (end > 0 && bytes[end - 1] == PAD) {
      end--;
    }
    int padding = bytes.length - end;
    if (padding > 6 || padding == 5 || padding == 2) {
      throw new IllegalArgumentException("Invalid Base32 padding");
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream(end * BITS_PER_CHAR / 8);
    int buffer = 0;
    int bitsInBuffer = 0;
    for (int i = 0; i < end; i++) {
      buffer = (buffer << BITS_PER_CHAR) | value(bytes[i]);
      bitsInBuffer += BITS_PER_CHAR;
      if (bitsInBuffer >= 8) {
        bitsInBuffer -= 8;
        out.write((buffer >> bitsInBuffer) & 0xFF);
      }
    }
    return out.toByteArray();
  }

  private int value(byte c) {
    int v = c >= 0 ? lookup[c] : -1;
    if (v < 0) {
      throw new IllegalArgumentException("Invalid Base32 character: '" + (char) c + "'");
    }
    return v;
  }
}
