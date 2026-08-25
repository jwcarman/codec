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
package org.jwcarman.codec.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.jwcarman.codec.spi.Codec;

/**
 * Envelope-encryption {@link Codec} for {@code byte[]}: encrypts with AES-256-GCM under a
 * per-message data-encryption key (DEK), itself wrapped by a {@link DataKeyProvider} and carried
 * alongside the ciphertext.
 *
 * <p>Built via {@link #builder(DataKeyProvider)}; the option set (strategy, AAD, allowlist, {@link
 * SecureRandom}) makes telescoping constructors unworkable:
 *
 * {@snippet lang = java :
 * EnvelopeCodec.builder(provider)          // required
 *     .strategy(DataKeyStrategy)           // default: DirectDataKeyStrategy
 *     .aad(byte[])                         // default: none; defensively copied
 *     .allowedKeyIds(Predicate<String>)    // default: provider.allowsKeyId
 *     .secureRandom(SecureRandom)          // default: new SecureRandom(); test seam
 *     .build();
 * }
 *
 * <h2>Ordering with compression</h2>
 *
 * <p>When composing with a compressing transform via {@link Codec#andThen(Codec)}, compression MUST
 * come before encryption ({@code .andThen(gzip).andThen(encryption)}): encrypted output is
 * high-entropy and does not compress, so compressing after encryption is useless. Compressing
 * attacker-influenced plaintext alongside secrets in the same stream can leak information about the
 * secret through the compressed length (the CRIME/BREACH class of attacks); this codec does not
 * defend against that, and callers compressing untrusted input alongside sensitive data should
 * evaluate that risk independently.
 *
 * <h2>Ciphertext-substitution limitation</h2>
 *
 * <p>AAD is fixed per {@code EnvelopeCodec} instance; there is no per-message context parameter.
 * The standard defense against ciphertext substitution &mdash; binding each message to the record
 * or context it belongs to &mdash; is therefore the caller's responsibility. Without it, an
 * attacker with write access to a datastore can swap one row's encrypted field into another row
 * under the same codec and key-encryption key, and decode will accept it cleanly. Callers storing
 * per-record encrypted fields should either construct a distinct codec per context where rows must
 * not be interchangeable, or bind identity inside the plaintext and verify it after decode.
 *
 * <h2>Cost of the default strategy</h2>
 *
 * <p>The default {@link DirectDataKeyStrategy} calls {@link DataKeyProvider#newDataKey()} on every
 * {@code encode}, which is one KMS round trip per message when the provider is backed by a remote
 * KMS. For small, frequent messages this cost, plus the wrapped-DEK overhead carried in every
 * message, can dominate; {@link BoundedDataKeyStrategy} amortizes both by reusing a data key across
 * a bounded number of messages or a bounded duration.
 *
 * <h2>KeyIds are not secret</h2>
 *
 * <p>The key id is carried in cleartext in every message; it is visible to anyone who can read the
 * ciphertext. KeyIds must not encode secrets.
 *
 * <h2>Wire format</h2>
 *
 * <p>The binary layout produced by {@code encode} is proprietary to this library and, once
 * persisted anywhere, permanent: nothing outside codec-crypto reads it, cross-language readers are
 * a non-goal, and a frozen test vector is the format's conformance spec.
 */
public final class EnvelopeCodec implements Codec<byte[]> {

  private static final byte MAGIC_0 = 0x4A;
  private static final byte MAGIC_1 = 0x43;
  private static final byte FORMAT_VERSION = 0x01;
  private static final byte ALGORITHM_AES_256_GCM = 0x01;
  private static final int NONCE_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8;
  private static final int FIXED_HEADER_LENGTH = 20;
  private static final int MIN_MESSAGE_LENGTH = FIXED_HEADER_LENGTH + 1 + 1 + TAG_LENGTH_BYTES;
  private static final String GCM_TRANSFORM = "AES/GCM/NoPadding";
  private static final String DEK_ALGORITHM = "AES";
  private static final int DEK_LENGTH_BYTES = 32;
  private static final int MAX_ECHOED_KEY_ID_LENGTH = 64;

  private final DataKeyProvider provider;
  private final DataKeyStrategy strategy;
  private final byte[] aad;
  private final Predicate<String> allowedKeyIds;
  private final SecureRandom random;
  private final Provider jceProvider;

  private EnvelopeCodec(Builder builder) {
    this.provider = builder.provider;
    this.strategy = builder.strategy;
    this.aad = builder.aad;
    this.allowedKeyIds =
        builder.allowedKeyIds != null ? builder.allowedKeyIds : builder.provider::allowsKeyId;
    this.random = builder.random;
    this.jceProvider = builder.jceProvider;
  }

  /**
   * Starts building an {@link EnvelopeCodec} backed by the given key provider.
   *
   * @param provider the {@link DataKeyProvider} used to acquire and unwrap data keys
   * @return a new {@link Builder}
   */
  public static Builder builder(DataKeyProvider provider) {
    return new Builder(provider);
  }

  @Override
  public byte[] encode(byte[] value) {
    Objects.requireNonNull(value, "value must not be null");
    DataKey dataKey;
    try {
      dataKey = strategy.acquire(provider);
    } catch (EncryptionException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new EncryptionException("Unable to acquire data key", e);
    }
    validateAes256(dataKey.key());
    byte[] keyIdBytes = dataKey.keyId().getBytes(StandardCharsets.UTF_8);
    byte[] wrapped = dataKey.wrapped();
    byte[] nonce = new byte[NONCE_LENGTH];
    random.nextBytes(nonce);

    int headerLength = FIXED_HEADER_LENGTH + keyIdBytes.length + wrapped.length;
    byte[] message = new byte[headerLength + value.length + TAG_LENGTH_BYTES];
    ByteBuffer header = ByteBuffer.wrap(message, 0, headerLength);
    // Field write order is normative wire format: version before algorithm (both are 0x01 today,
    // so no test can observe a swap, but decode reads them in this order).
    header.put(MAGIC_0).put(MAGIC_1).put(FORMAT_VERSION).put(ALGORITHM_AES_256_GCM);
    header.putShort((short) keyIdBytes.length).put(keyIdBytes);
    header.putShort((short) wrapped.length).put(wrapped);
    header.put(nonce);

    try {
      byte[] sealed =
          gcmEncrypt(
              jceProvider, dataKey.key(), nonce, Arrays.copyOf(message, headerLength), aad, value);
      System.arraycopy(sealed, 0, message, headerLength, sealed.length);
    } catch (GeneralSecurityException e) {
      throw new EncryptionException("Unable to encrypt data", e);
    }
    return message;
  }

  @Override
  public byte[] decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    if (bytes.length < 2) {
      throw new DecryptionException("message too short: " + bytes.length + " bytes");
    }
    if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) {
      throw new DecryptionException("bad magic: not an envelope");
    }
    if (bytes.length < MIN_MESSAGE_LENGTH) {
      throw new DecryptionException("message too short: " + bytes.length + " bytes");
    }
    if (bytes[2] != FORMAT_VERSION) {
      throw new DecryptionException("unknown format version: " + bytes[2]);
    }
    if (bytes[3] != ALGORITHM_AES_256_GCM) {
      throw new DecryptionException("unknown algorithm id: " + bytes[3]);
    }
    int keyIdLength = readUint16(bytes, 4);
    if (keyIdLength < 1 || 6 + keyIdLength + 2 > bytes.length) {
      throw new DecryptionException("invalid keyId length: " + keyIdLength);
    }
    int wrappedOffset = 6 + keyIdLength;
    int wrappedLength = readUint16(bytes, wrappedOffset);
    int headerLength = FIXED_HEADER_LENGTH + keyIdLength + wrappedLength;
    if (wrappedLength < 1 || headerLength + TAG_LENGTH_BYTES > bytes.length) {
      throw new DecryptionException("invalid wrapped key length: " + wrappedLength);
    }
    String keyId = new String(bytes, 6, keyIdLength, StandardCharsets.UTF_8);
    if (!allowedKeyIds.test(keyId)) {
      throw new DecryptionException("keyId is not allowed: " + sanitizeForMessage(keyId));
    }
    byte[] wrapped =
        Arrays.copyOfRange(bytes, wrappedOffset + 2, wrappedOffset + 2 + wrappedLength);
    SecretKey dek = unwrapDataKey(keyId, wrapped);
    byte[] nonce = Arrays.copyOfRange(bytes, headerLength - NONCE_LENGTH, headerLength);
    try {
      return gcmDecrypt(
          jceProvider,
          dek,
          nonce,
          Arrays.copyOf(bytes, headerLength),
          aad,
          bytes,
          headerLength,
          bytes.length - headerLength);
    } catch (GeneralSecurityException e) {
      throw DecryptionException.cryptographic(e);
    }
  }

  private static void validateAes256(SecretKey key) {
    if (!DEK_ALGORITHM.equals(key.getAlgorithm())) {
      throw new EncryptionException(
          "Data key algorithm mismatch: expected AES, got " + key.getAlgorithm());
    }
    byte[] encoded = key.getEncoded();
    // A null encoding means an opaque, HSM-backed key: its length cannot be checked here, so it
    // is trusted to be AES-256 as the provider contract requires.
    if (encoded != null && encoded.length != DEK_LENGTH_BYTES) {
      throw new EncryptionException("Data key length mismatch: expected 32 bytes");
    }
  }

  private SecretKey unwrapDataKey(String keyId, byte[] wrapped) {
    try {
      return provider.unwrap(keyId, wrapped);
    } catch (DecryptionException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeyAccessException("Key infrastructure unavailable", e);
    }
  }

  /**
   * Renders an untrusted, wire-supplied keyId safe to embed in an exception message: truncates to
   * {@value #MAX_ECHOED_KEY_ID_LENGTH} characters and replaces every ISO control character (C0
   * below {@code 0x20}, {@code 0x7F}, and the C1 range {@code 0x80}&ndash;{@code 0x9F}), the
   * Unicode line and paragraph separators ({@code U+2028}, {@code U+2029}), and every Unicode
   * format character (category Cf, which includes the bidirectional-override characters used to
   * spoof displayed text direction) with {@code '?'}, so a malicious or corrupted keyId cannot
   * inject control characters, reorder the surrounding message visually, or grow the message
   * without bound.
   */
  private static String sanitizeForMessage(String keyId) {
    String truncated = keyId.substring(0, Math.min(keyId.length(), MAX_ECHOED_KEY_ID_LENGTH));
    StringBuilder sanitized = new StringBuilder(truncated.length());
    for (int i = 0; i < truncated.length(); i++) {
      char c = truncated.charAt(i);
      boolean deny =
          Character.isISOControl(c)
              || c == '\u2028'
              || c == '\u2029'
              || Character.getType(c) == Character.FORMAT;
      sanitized.append(deny ? '?' : c);
    }
    return sanitized.toString();
  }

  private static int readUint16(byte[] bytes, int offset) {
    return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
  }

  private static void checkTransform(String transform, Provider provider) {
    try {
      newCipher(transform, provider);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(
          "provider "
              + (provider == null ? "<default>" : provider.getName())
              + " cannot supply "
              + transform,
          e);
    }
  }

  static Cipher newCipher(String transform, Provider provider) throws GeneralSecurityException {
    return provider == null
        ? Cipher.getInstance(transform)
        : Cipher.getInstance(transform, provider);
  }

  /**
   * Encrypts {@code plaintext} under AES-256-GCM, authenticating {@code headerAad} followed by
   * {@code extraAad} (when non-null) as additional authenticated data.
   *
   * @param provider the {@link Provider} to resolve the {@code AES/GCM/NoPadding} {@link Cipher}
   *     from, or {@code null} for the JDK's default provider lookup
   * @param key the AES-256 key to encrypt under
   * @param nonce the GCM nonce
   * @param headerAad the header bytes to authenticate, applied first
   * @param extraAad additional AAD to authenticate after {@code headerAad}, or {@code null}
   * @param plaintext the plaintext to encrypt
   * @return the ciphertext followed by the authentication tag
   * @throws GeneralSecurityException if the provider cannot supply the transform or encryption
   *     fails
   */
  static byte[] gcmEncrypt(
      Provider provider,
      SecretKey key,
      byte[] nonce,
      byte[] headerAad,
      byte[] extraAad,
      byte[] plaintext)
      throws GeneralSecurityException {
    Cipher cipher = newCipher(GCM_TRANSFORM, provider);
    cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
    cipher.updateAAD(headerAad);
    if (extraAad != null) {
      cipher.updateAAD(extraAad);
    }
    return cipher.doFinal(plaintext);
  }

  /**
   * Decrypts {@code data[offset, offset + length)} under AES-256-GCM, authenticating {@code
   * headerAad} followed by {@code extraAad} (when non-null) as additional authenticated data.
   *
   * @param provider the {@link Provider} to resolve the {@code AES/GCM/NoPadding} {@link Cipher}
   *     from, or {@code null} for the JDK's default provider lookup
   * @param key the AES-256 key to decrypt under
   * @param nonce the GCM nonce
   * @param headerAad the header bytes to authenticate, applied first
   * @param extraAad additional AAD to authenticate after {@code headerAad}, or {@code null}
   * @param data the buffer containing the ciphertext followed by the authentication tag
   * @param offset the offset of the ciphertext within {@code data}
   * @param length the length, in bytes, of the ciphertext plus tag
   * @return the decrypted plaintext
   * @throws GeneralSecurityException if the provider cannot supply the transform or tag
   *     verification fails
   */
  static byte[] gcmDecrypt(
      Provider provider,
      SecretKey key,
      byte[] nonce,
      byte[] headerAad,
      byte[] extraAad,
      byte[] data,
      int offset,
      int length)
      throws GeneralSecurityException {
    Cipher cipher = newCipher(GCM_TRANSFORM, provider);
    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
    cipher.updateAAD(headerAad);
    if (extraAad != null) {
      cipher.updateAAD(extraAad);
    }
    return cipher.doFinal(data, offset, length);
  }

  /** Builder for {@link EnvelopeCodec}. */
  public static final class Builder {

    private final DataKeyProvider provider;
    private DataKeyStrategy strategy = new DirectDataKeyStrategy();
    private byte[] aad;
    private Predicate<String> allowedKeyIds;
    private SecureRandom random = new SecureRandom();
    private Provider jceProvider;

    private Builder(DataKeyProvider provider) {
      this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    /**
     * Sets the strategy used to acquire the data key for each message.
     *
     * @param strategy the {@link DataKeyStrategy} to use; defaults to {@link DirectDataKeyStrategy}
     * @return this builder
     */
    public Builder strategy(DataKeyStrategy strategy) {
      this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
      return this;
    }

    /**
     * Sets the additional authenticated data (AAD) bound to every message produced by this codec.
     *
     * @param aad the AAD bytes, defensively copied; must not be empty
     * @return this builder
     * @throws IllegalArgumentException if {@code aad} is empty
     */
    public Builder aad(byte[] aad) {
      Objects.requireNonNull(aad, "aad must not be null");
      if (aad.length == 0) {
        throw new IllegalArgumentException(
            "aad must not be empty: empty and absent AAD are cryptographically identical");
      }
      this.aad = aad.clone();
      return this;
    }

    /**
     * Sets the predicate used to admit a wire keyId on decode, overriding the provider's {@link
     * DataKeyProvider#allowsKeyId(String)}.
     *
     * @param allowedKeyIds the predicate to consult; defaults to {@code provider::allowsKeyId}
     * @return this builder
     */
    public Builder allowedKeyIds(Predicate<String> allowedKeyIds) {
      this.allowedKeyIds = Objects.requireNonNull(allowedKeyIds, "allowedKeyIds must not be null");
      return this;
    }

    /**
     * Sets the source of randomness used to generate nonces.
     *
     * @param random the {@link SecureRandom} to use; defaults to a new instance
     * @return this builder
     */
    public Builder secureRandom(SecureRandom random) {
      this.random = Objects.requireNonNull(random, "random must not be null");
      return this;
    }

    /**
     * Sets the {@link Provider} used to look up the {@code AES/GCM/NoPadding} {@link Cipher} for
     * every encode and decode, so a FIPS-validated or otherwise pinned provider can be selected per
     * codec instead of being installed as the JVM's globally highest-priority provider.
     *
     * <p>Resolved once at {@link #build()} time: if the provider cannot supply the transform,
     * {@code build()} throws {@link IllegalStateException} immediately rather than letting the
     * misconfiguration surface later as a {@link DecryptionException} on the first message.
     *
     * @param provider the {@link Provider} to use; defaults to the JDK's default provider lookup
     * @return this builder
     */
    public Builder provider(Provider provider) {
      this.jceProvider = Objects.requireNonNull(provider, "provider must not be null");
      return this;
    }

    /**
     * Builds the {@link EnvelopeCodec}.
     *
     * @return a new {@link EnvelopeCodec}
     * @throws IllegalStateException if a provider was set and cannot supply {@code
     *     AES/GCM/NoPadding}
     */
    public EnvelopeCodec build() {
      checkTransform(GCM_TRANSFORM, jceProvider);
      return new EnvelopeCodec(this);
    }
  }
}
