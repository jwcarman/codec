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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;

/**
 * A byte-to-text-safe-byte transform using Base32: eight ASCII symbols per five bytes, over any
 * alphabet of 32 symbols. Base32 is larger than Base64 but its standard alphabet has no lower-case
 * letters and no symbols, which makes it safe for case-insensitive contexts — DNS labels, file
 * names, and values a person will read aloud or type. It is the encoding used for TOTP secrets.
 *
 * <p>{@link #standard()} and {@link #hex()} are RFC 4648 §6 and §7: upper-case output with {@code
 * =} padding. {@link #of(String)} and {@link #of(String, char)} take any 32-symbol alphabet —
 * z-base-32, geohash, an alphabet of your own — with the same bit layout (RFC 4648 §3: input bytes
 * most-significant bit first, five bits per symbol, the final partial symbol left-aligned with zero
 * bits, and with padding the output padded to a whole group of eight).
 *
 * <p>Decoding is strict and canonical: it accepts exactly the strings {@link #encode} can produce.
 * A symbol outside the alphabet, a length or padding that does not correspond to whole input bytes,
 * a pad symbol before the end, or non-zero trailing bits in the final symbol is rejected with
 * {@link InvalidPayloadException}. Canonical decoding gives every value exactly one encoded form,
 * so encoded strings can be compared, deduplicated and signed (RFC 4648 §12). Lower-case input is
 * an opt-in: {@link #caseInsensitive()}. {@link #aliasing(Map)} accepts extra characters as named
 * symbols; {@link #crockford()} uses both, and {@link #zBase32()} and {@link #geohash()} are
 * further strict presets.
 *
 * <p>Like {@link Base64Codec}, put it <em>last</em> in a chain. Instances are immutable and
 * thread-safe.
 */
public final class Base32Codec implements Codec<byte[]> {

  private static final String STANDARD_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final String HEX_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUV";
  private static final char RFC_PAD = '=';
  private static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  private static final Map<Character, Character> CROCKFORD_ALIASES =
      Map.of('I', '1', 'i', '1', 'L', '1', 'l', '1', 'O', '0', 'o', '0');
  private static final String Z_BASE_32_ALPHABET = "ybndrfg8ejkmcpqxot1uwisza345h769";
  private static final String GEOHASH_ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz";

  private static final int ALPHABET_SIZE = 32;
  private static final int MASK = 0x1F;
  private static final int BITS = 5;
  private static final int GROUP_BYTES = 5;
  private static final int GROUP_SYMBOLS = 8;
  private static final int NO_PAD = -1;
  private static final int ASCII_LIMIT = 128;
  private static final byte NOT_A_SYMBOL = -1;

  private final byte[] alphabet;
  private final byte[] lookup;
  private final int pad;

  private Base32Codec(byte[] alphabet, byte[] lookup, int pad) {
    this.alphabet = alphabet;
    this.lookup = lookup;
    this.pad = pad;
  }

  /**
   * The standard alphabet ({@code A-Z2-7}) with {@code =} padding, used by TOTP secrets and most
   * Base32 consumers. Decoding is strict: upper case only.
   *
   * @return a codec for RFC 4648 §6 Base32
   */
  public static Base32Codec standard() {
    return of(STANDARD_ALPHABET, RFC_PAD);
  }

  /**
   * The "extended hex" alphabet ({@code 0-9A-V}) with {@code =} padding, whose encoded form sorts
   * in the same order as the bytes it encodes — useful for sortable keys. Decoding is strict: upper
   * case only.
   *
   * @return a codec for RFC 4648 §7 base32hex
   */
  public static Base32Codec hex() {
    return of(HEX_ALPHABET, RFC_PAD);
  }

  /**
   * Crockford's alphabet ({@code 0-9A-Z} without {@code I}, {@code L}, {@code O}, {@code U}), the
   * encoding of ULIDs: no padding, and decoding that folds case and reads {@code I} and {@code L}
   * as {@code 1} and {@code O} as {@code 0}. Crockford's optional check symbol and hyphens are not
   * accepted.
   *
   * @return a codec for Crockford Base32
   */
  public static Base32Codec crockford() {
    return of(CROCKFORD_ALPHABET).caseInsensitive().aliasing(CROCKFORD_ALIASES);
  }

  /**
   * z-base-32 ({@code ybndrfg8ejkmcpqxot1uwisza345h769}), the alphabet chosen for ease of
   * handwriting and reading aloud, as used by Tahoe-LAFS and Phil Zimmermann's ZRTP: lower case, no
   * padding, strict decoding.
   *
   * @return a codec for z-base-32
   */
  public static Base32Codec zBase32() {
    return of(Z_BASE_32_ALPHABET);
  }

  /**
   * The geohash alphabet ({@code 0-9b-z} without {@code a}, {@code i}, {@code l}, {@code o}): lower
   * case, no padding, strict decoding. Use {@link #caseInsensitive()} to accept upper-case
   * geohashes.
   *
   * @return a codec for the geohash alphabet
   */
  public static Base32Codec geohash() {
    return of(GEOHASH_ALPHABET);
  }

  /**
   * A strict codec over {@code alphabet} with no padding.
   *
   * @param alphabet the 32 symbols in value order; distinct ASCII characters
   * @return a strict, unpadded codec
   * @throws NullPointerException if {@code alphabet} is null
   * @throws IllegalArgumentException if the alphabet does not have exactly 32 symbols, or a symbol
   *     is not ASCII, or a symbol appears twice
   */
  public static Base32Codec of(String alphabet) {
    return create(alphabet, NO_PAD);
  }

  /**
   * A strict codec over {@code alphabet}, padding the output to a whole group of eight symbols with
   * {@code pad}.
   *
   * @param alphabet the 32 symbols in value order; distinct ASCII characters
   * @param pad the pad symbol, an ASCII character that is not in the alphabet
   * @return a strict, padded codec
   * @throws NullPointerException if {@code alphabet} is null
   * @throws IllegalArgumentException if the alphabet does not have exactly 32 symbols, or a symbol
   *     is not ASCII, or a symbol appears twice, or the pad symbol is not ASCII or is in the
   *     alphabet
   */
  public static Base32Codec of(String alphabet, char pad) {
    Objects.requireNonNull(alphabet, "alphabet must not be null");
    if (pad >= ASCII_LIMIT) {
      throw new IllegalArgumentException("pad symbol must be ASCII: " + describe(pad));
    }
    return create(alphabet, pad);
  }

  private static Base32Codec create(String alphabet, int pad) {
    Objects.requireNonNull(alphabet, "alphabet must not be null");
    int size = alphabet.length();
    if (size != ALPHABET_SIZE) {
      throw new IllegalArgumentException("alphabet must have 32 symbols: " + size);
    }
    byte[] symbols = new byte[size];
    byte[] lookup = new byte[ASCII_LIMIT];
    Arrays.fill(lookup, NOT_A_SYMBOL);
    for (int i = 0; i < size; i++) {
      char c = alphabet.charAt(i);
      if (c >= lookup.length) {
        throw new IllegalArgumentException("alphabet symbol must be ASCII: " + describe(c));
      }
      if (lookup[c] != NOT_A_SYMBOL) {
        throw new IllegalArgumentException("alphabet symbol appears twice: " + describe(c));
      }
      symbols[i] = (byte) c;
      lookup[c] = (byte) i;
    }
    if (pad != NO_PAD && lookup[pad] != NOT_A_SYMBOL) {
      throw new IllegalArgumentException("pad symbol is in the alphabet: " + describe(pad));
    }
    return new Base32Codec(symbols, lookup, pad);
  }

  /**
   * The alphabet, in value order.
   *
   * @return the 32 symbols
   */
  public String alphabet() {
    return new String(alphabet, StandardCharsets.US_ASCII);
  }

  /**
   * The same alphabet and padding, decoding without regard to letter case: {@code a} and {@code A}
   * both read as whichever of them is in the alphabet. Encoding is unchanged and still emits the
   * alphabet's symbols exactly. The original codec is unaffected.
   *
   * @return a codec that folds case on decode
   * @throws IllegalArgumentException if two alphabet symbols differ only by case, or the pad symbol
   *     differs from an alphabet symbol only by case, so folding would be ambiguous
   */
  public Base32Codec caseInsensitive() {
    byte[] folded = lookup.clone();
    for (int i = 0; i < alphabet.length; i++) {
      char symbol = (char) alphabet[i];
      char other =
          Character.isUpperCase(symbol)
              ? Character.toLowerCase(symbol)
              : Character.toUpperCase(symbol);
      if (other == symbol) {
        continue;
      }
      if (other == pad) {
        throw new IllegalArgumentException(
            "pad symbol " + describe(other) + " differs from a symbol only by case");
      }
      if (folded[other] != NOT_A_SYMBOL && folded[other] != i) {
        throw new IllegalArgumentException(
            "case folding would make " + describe(other) + " ambiguous");
      }
      folded[other] = (byte) i;
    }
    return new Base32Codec(alphabet, folded, pad);
  }

  /**
   * The same codec, additionally reading each key of {@code aliases} as the alphabet symbol it
   * names — Crockford's {@code I}, {@code L} → {@code 1} and {@code O} → {@code 0}, for example.
   * Aliases are exact characters: to accept both cases of an alias, list both. Encoding is
   * unchanged. The original codec is unaffected.
   *
   * @param aliases characters to accept on decode, each mapped to the alphabet symbol it stands for
   * @return a codec that also accepts the aliases
   * @throws NullPointerException if {@code aliases} is null
   * @throws IllegalArgumentException if an alias is not ASCII, is the pad symbol, or is already
   *     accepted (an alphabet symbol, a folded case, or an earlier alias); or if a target is not an
   *     alphabet symbol
   */
  public Base32Codec aliasing(Map<Character, Character> aliases) {
    Objects.requireNonNull(aliases, "aliases must not be null");
    byte[] aliased = lookup.clone();
    for (Map.Entry<Character, Character> alias : aliases.entrySet()) {
      char from = alias.getKey();
      char to = alias.getValue();
      if (from >= ASCII_LIMIT) {
        throw new IllegalArgumentException("alias must be ASCII: " + describe(from));
      }
      if (from == pad) {
        throw new IllegalArgumentException("alias is the pad symbol: " + describe(from));
      }
      if (aliased[from] != NOT_A_SYMBOL) {
        throw new IllegalArgumentException("alias is already a symbol: " + describe(from));
      }
      if (to >= ASCII_LIMIT || lookup[to] == NOT_A_SYMBOL || alphabet[lookup[to]] != to) {
        throw new IllegalArgumentException(
            "alias target is not an alphabet symbol: " + describe(to));
      }
      aliased[from] = lookup[to];
    }
    return new Base32Codec(alphabet, aliased, pad);
  }

  @Override
  public byte[] encode(byte[] value) {
    Objects.requireNonNull(value, "value must not be null");
    int full = value.length / GROUP_BYTES;
    int rem = value.length - full * GROUP_BYTES;
    int tailSymbols = rem == 0 ? 0 : symbolsFor(rem);
    int length = full * GROUP_SYMBOLS + (rem == 0 || pad == NO_PAD ? tailSymbols : GROUP_SYMBOLS);
    byte[] out = new byte[length];
    encodeGroups(value, full, out);
    if (rem != 0) {
      encodeTail(value, full * GROUP_BYTES, rem, out, full * GROUP_SYMBOLS, tailSymbols);
    }
    return out;
  }

  /**
   * Five bytes to eight symbols with literal shifts; the JIT unrolls what a generic loop cannot.
   */
  private void encodeGroups(byte[] in, int groups, byte[] out) {
    byte[] a = alphabet;
    int i = 0;
    int o = 0;
    for (int g = 0; g < groups; g++) {
      long v =
          ((long) (in[i] & 0xFF) << 32)
              | ((long) (in[i + 1] & 0xFF) << 24)
              | ((in[i + 2] & 0xFF) << 16)
              | ((in[i + 3] & 0xFF) << 8)
              | (in[i + 4] & 0xFF);
      out[o] = a[(int) (v >>> 35) & MASK];
      out[o + 1] = a[(int) (v >>> 30) & MASK];
      out[o + 2] = a[(int) (v >>> 25) & MASK];
      out[o + 3] = a[(int) (v >>> 20) & MASK];
      out[o + 4] = a[(int) (v >>> 15) & MASK];
      out[o + 5] = a[(int) (v >>> 10) & MASK];
      out[o + 6] = a[(int) (v >>> 5) & MASK];
      out[o + 7] = a[(int) v & MASK];
      i += GROUP_BYTES;
      o += GROUP_SYMBOLS;
    }
  }

  private void encodeTail(byte[] in, int from, int count, byte[] out, int at, int symbols) {
    long acc = 0;
    for (int j = 0; j < count; j++) {
      acc = (acc << 8) | (in[from + j] & 0xFF);
    }
    acc <<= symbols * BITS - count * 8; // left-align: the trailing bits are zero
    for (int s = symbols - 1; s >= 0; s--) {
      out[at + s] = alphabet[(int) (acc & MASK)];
      acc >>>= BITS;
    }
    if (pad != NO_PAD) {
      Arrays.fill(out, at + symbols, out.length, (byte) pad);
    }
  }

  @Override
  public byte[] decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    int end = unpaddedLength(bytes);
    int full = end / GROUP_SYMBOLS;
    int tailSymbols = end - full * GROUP_SYMBOLS;
    int tailBytes = tailSymbols * BITS / 8;
    if (tailSymbols != 0 && (tailBytes == 0 || symbolsFor(tailBytes) != tailSymbols)) {
      throw new InvalidPayloadException(
          pad == NO_PAD
              ? "Invalid length: " + tailSymbols + " trailing symbols do not encode whole bytes"
              : "Invalid padding: "
                  + tailSymbols
                  + " symbols before the padding do not encode whole bytes");
    }
    byte[] out = new byte[full * GROUP_BYTES + tailBytes];
    decodeGroups(bytes, full, out);
    if (tailSymbols != 0) {
      decodeTail(bytes, full * GROUP_SYMBOLS, tailSymbols, out, full * GROUP_BYTES, tailBytes);
    }
    return out;
  }

  private int unpaddedLength(byte[] bytes) {
    if (pad == NO_PAD) {
      return bytes.length;
    }
    if (bytes.length % GROUP_SYMBOLS != 0) {
      throw new InvalidPayloadException(
          "Base32 input length must be a multiple of " + GROUP_SYMBOLS + ": " + bytes.length);
    }
    int end = bytes.length;
    while (end > 0 && bytes[end - 1] == pad) {
      end--;
    }
    if (bytes.length - end >= GROUP_SYMBOLS) {
      throw new InvalidPayloadException("Invalid padding: a whole group of pad symbols");
    }
    return end;
  }

  /** Eight symbols to five bytes with literal shifts. */
  private void decodeGroups(byte[] in, int groups, byte[] out) {
    int i = 0;
    int o = 0;
    for (int g = 0; g < groups; g++) {
      long v =
          ((long) valueOf(in[i]) << 35)
              | ((long) valueOf(in[i + 1]) << 30)
              | ((long) valueOf(in[i + 2]) << 25)
              | ((long) valueOf(in[i + 3]) << 20)
              | ((long) valueOf(in[i + 4]) << 15)
              | ((long) valueOf(in[i + 5]) << 10)
              | ((long) valueOf(in[i + 6]) << 5)
              | valueOf(in[i + 7]);
      out[o] = (byte) (v >>> 32);
      out[o + 1] = (byte) (v >>> 24);
      out[o + 2] = (byte) (v >>> 16);
      out[o + 3] = (byte) (v >>> 8);
      out[o + 4] = (byte) v;
      i += GROUP_SYMBOLS;
      o += GROUP_BYTES;
    }
  }

  private void decodeTail(byte[] in, int from, int symbols, byte[] out, int at, int count) {
    long acc = 0;
    for (int s = 0; s < symbols; s++) {
      acc = (acc << BITS) | valueOf(in[from + s]);
    }
    int trailing = symbols * BITS - count * 8;
    if ((acc & ((1L << trailing) - 1)) != 0) {
      throw new InvalidPayloadException("Non-zero trailing bits in the final symbol");
    }
    acc >>>= trailing;
    for (int j = count - 1; j >= 0; j--) {
      out[at + j] = (byte) acc;
      acc >>>= 8;
    }
  }

  private int valueOf(byte c) {
    int v = c >= 0 ? lookup[c] : NOT_A_SYMBOL;
    if (v == NOT_A_SYMBOL) {
      throw invalidSymbol(c);
    }
    return v;
  }

  private InvalidPayloadException invalidSymbol(byte c) {
    int code = c & 0xFF;
    if (code == pad) {
      return new InvalidPayloadException(
          "Invalid padding: " + describe(code) + " before the end of the input");
    }
    return new InvalidPayloadException("Invalid character: " + describe(code));
  }

  private static int symbolsFor(int byteCount) {
    return (byteCount * 8 + BITS - 1) / BITS;
  }

  private static String describe(int c) {
    return c >= 0x20 && c < 0x7F ? "'" + (char) c + "'" : String.format("U+%04X", c);
  }
}
