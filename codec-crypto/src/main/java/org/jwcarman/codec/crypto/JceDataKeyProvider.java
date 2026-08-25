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

import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * In-process {@link DataKeyProvider} for consumers without a KMS, backed by one or more
 * key-encryption keys (KEKs) supplied at construction.
 *
 * <p>The constructor takes a {@code Map<String, SecretKey>} of AES-256 KEKs keyed by id, plus the
 * id of the current wrapping KEK. Generated data-encryption keys (DEKs) are AES-256, drawn from
 * {@link SecureRandom}, and wrapped with AES key-wrap ({@code AESWrap}, RFC 3394): unlike GCM,
 * AES-KW needs no nonce, so wrapping the same DEK under the same KEK always produces the same
 * ciphertext, and its integrity check value (ICV) causes unwrap of a tampered or foreign-key blob
 * to fail rather than silently return garbage key material.
 *
 * <p>The blob returned as {@link DataKey#wrapped()} is {@code [scheme:1][payload]}: a one-byte
 * wrap-scheme tag ({@link #WRAP_SCHEME_AES_KW}, {@code 0x01}) followed by the AES-KW payload — 41
 * bytes total for a 32-byte DEK. This is invisible to {@code EnvelopeCodec}, which treats the whole
 * blob as opaque; it exists so this provider has the same wrap-algorithm migration story a
 * KMS-backed provider gets from its own versioned ciphertext format. Scheme values {@code 0x02} and
 * above are reserved for future wrap algorithms; {@link #unwrap(String, byte[])} rejects any blob
 * shorter than 2 bytes or tagged with an unrecognized scheme.
 *
 * <p>The KEK map <strong>is</strong> this provider's allowlist: {@link #allowsKeyId(String)} and
 * {@link #unwrap(String, byte[])} both consult it directly, so any key id absent from the map is
 * rejected — this satisfies the {@link DataKeyProvider} security contract without any separate
 * configuration. New data keys always wrap under the configured current KEK, while {@code unwrap}
 * accepts any KEK present in the map; this asymmetry is what lets a consumer rotate KEKs by adding
 * a new entry, pointing {@code currentKeyId} at it, and keeping the old entry only until every
 * message wrapped under it has been re-encrypted.
 *
 * <p>The two public constructors cover the simple default case: a new {@link SecureRandom}
 * instance, or a caller-supplied one as a test seam, with {@code AESWrap} resolved via the JDK's
 * default provider lookup. Use {@link #builder(String, Map)} instead when a {@link
 * java.security.Provider} needs to be selected explicitly — for example a FIPS-validated provider
 * pinned to this instance rather than installed globally; the builder also exposes the {@link
 * SecureRandom} seam via {@code secureRandom(SecureRandom)}.
 */
public final class JceDataKeyProvider implements DataKeyProvider {

  private static final String AES = "AES";
  private static final String WRAP_TRANSFORM = "AES/KW/NoPadding";
  private static final int DEK_LENGTH_BYTES = 32;

  /**
   * Wrap-scheme tag for the first byte of {@link DataKey#wrapped()} blobs produced by this
   * provider: AES key-wrap (RFC 3394) over the DEK. Scheme values {@code 0x02} and above are
   * reserved for future wrap algorithms; {@link #unwrap(String, byte[])} rejects any blob whose
   * first byte is not a scheme it recognizes.
   */
  static final byte WRAP_SCHEME_AES_KW = 0x01;

  private final String currentKeyId;
  private final Map<String, SecretKey> keks;
  private final SecureRandom random;
  private final Provider provider;

  /**
   * Creates a provider using a default {@link SecureRandom} instance.
   *
   * @param currentKeyId the id of the KEK, present in {@code keks}, used to wrap new data keys
   * @param keks the AES-256 key-encryption keys this provider trusts, keyed by id
   * @throws IllegalStateException if the default JCE provider cannot supply the AESWrap transform
   */
  public JceDataKeyProvider(String currentKeyId, Map<String, SecretKey> keks) {
    this(currentKeyId, keks, new SecureRandom(), null);
  }

  /**
   * Creates a provider using the given {@link SecureRandom} for data-key generation.
   *
   * @param currentKeyId the id of the KEK, present in {@code keks}, used to wrap new data keys
   * @param keks the AES-256 key-encryption keys this provider trusts, keyed by id
   * @param random the source of randomness for generated data keys
   * @throws IllegalStateException if the default JCE provider cannot supply the AESWrap transform
   */
  public JceDataKeyProvider(String currentKeyId, Map<String, SecretKey> keks, SecureRandom random) {
    this(currentKeyId, keks, random, null);
  }

  private JceDataKeyProvider(
      String currentKeyId, Map<String, SecretKey> keks, SecureRandom random, Provider provider) {
    Objects.requireNonNull(currentKeyId, "currentKeyId must not be null");
    Objects.requireNonNull(keks, "keks must not be null");
    this.random = Objects.requireNonNull(random, "random must not be null");
    // Copy first, then validate against the copy: keks is caller-owned and may be a mutable map
    // shared with other code, so validating a live reference to it would let a concurrent
    // mutation seat an unvalidated KEK between the check and the assignment.
    this.keks = Map.copyOf(keks);
    if (this.keks.isEmpty()) {
      throw new IllegalArgumentException("keks must not be empty");
    }
    if (!this.keks.containsKey(currentKeyId)) {
      throw new IllegalArgumentException("currentKeyId is not in the KEK map: " + currentKeyId);
    }
    this.keks.forEach(
        (id, key) -> {
          if (!AES.equals(key.getAlgorithm())) {
            throw new IllegalArgumentException(
                "KEK " + id + " is not an AES key: algorithm is " + key.getAlgorithm());
          }
          byte[] encoded = key.getEncoded();
          if (encoded != null && encoded.length != DEK_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                "KEK " + id + " is not AES-256: encoded length is " + encoded.length + " bytes");
          }
        });
    this.currentKeyId = currentKeyId;
    this.provider = provider;
    try {
      wrapCipher();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(
          "provider "
              + (provider == null ? "<default>" : provider.getName())
              + " cannot supply "
              + WRAP_TRANSFORM,
          e);
    }
  }

  private Cipher wrapCipher() throws GeneralSecurityException {
    return provider == null
        ? Cipher.getInstance(WRAP_TRANSFORM)
        : Cipher.getInstance(WRAP_TRANSFORM, provider);
  }

  @Override
  public DataKey newDataKey() {
    byte[] dekBytes = new byte[DEK_LENGTH_BYTES];
    random.nextBytes(dekBytes);
    SecretKey dek = aesKeyFrom(dekBytes);
    try {
      Cipher cipher = wrapCipher();
      cipher.init(Cipher.WRAP_MODE, keks.get(currentKeyId));
      byte[] payload = cipher.wrap(dek);
      byte[] blob = new byte[1 + payload.length];
      blob[0] = WRAP_SCHEME_AES_KW;
      System.arraycopy(payload, 0, blob, 1, payload.length);
      return new DataKey(currentKeyId, dek, blob);
    } catch (GeneralSecurityException e) {
      throw new EncryptionException("Unable to wrap data key", e);
    }
  }

  /**
   * Wraps {@code material} in an AES {@link SecretKey} and then zeroes {@code material} in place,
   * since {@link SecretKeySpec} copies the bytes it is given rather than aliasing the array.
   *
   * @param material the raw key bytes; zeroed as a side effect of this call
   * @return an AES {@link SecretKey} over a copy of {@code material}'s original contents
   */
  static SecretKey aesKeyFrom(byte[] material) {
    SecretKey key = new SecretKeySpec(material, AES);
    Arrays.fill(material, (byte) 0);
    return key;
  }

  @Override
  public SecretKey unwrap(String keyId, byte[] wrapped) {
    SecretKey kek = keks.get(keyId);
    if (kek == null) {
      throw DecryptionException.cryptographic(null);
    }
    if (wrapped.length < 2 || wrapped[0] != WRAP_SCHEME_AES_KW) {
      throw DecryptionException.cryptographic(null);
    }
    byte[] payload = Arrays.copyOfRange(wrapped, 1, wrapped.length);
    try {
      Cipher cipher = wrapCipher();
      cipher.init(Cipher.UNWRAP_MODE, kek);
      return (SecretKey) cipher.unwrap(payload, AES, Cipher.SECRET_KEY);
    } catch (GeneralSecurityException e) {
      throw DecryptionException.cryptographic(e);
    }
  }

  @Override
  public boolean allowsKeyId(String keyId) {
    return keks.containsKey(keyId);
  }

  /**
   * Starts building a {@link JceDataKeyProvider}.
   *
   * @param currentKeyId the id of the KEK, present in {@code keks}, used to wrap new data keys
   * @param keks the AES-256 key-encryption keys this provider trusts, keyed by id
   * @return a new {@link Builder}
   */
  public static Builder builder(String currentKeyId, Map<String, SecretKey> keks) {
    return new Builder(currentKeyId, keks);
  }

  /** Builder for {@link JceDataKeyProvider}. */
  public static final class Builder {
    private final String currentKeyId;
    private final Map<String, SecretKey> keks;
    private SecureRandom random = new SecureRandom();
    private Provider provider;

    private Builder(String currentKeyId, Map<String, SecretKey> keks) {
      this.currentKeyId = Objects.requireNonNull(currentKeyId, "currentKeyId must not be null");
      this.keks = Objects.requireNonNull(keks, "keks must not be null");
    }

    /**
     * Sets the source of randomness used to generate data keys.
     *
     * @param random the {@link SecureRandom} to use; defaults to a new instance
     * @return this builder
     */
    public Builder secureRandom(SecureRandom random) {
      this.random = Objects.requireNonNull(random, "random must not be null");
      return this;
    }

    /**
     * Sets the {@link Provider} used to look up the {@code AESWrap} {@link Cipher} for wrapping and
     * unwrapping data keys, so a FIPS-validated or otherwise pinned provider can be selected per
     * provider instance instead of being installed as the JVM's globally highest-priority provider.
     *
     * <p>Resolved once at {@link #build()} time: if the provider cannot supply the transform,
     * {@code build()} throws {@link IllegalStateException} immediately rather than letting the
     * misconfiguration surface later as a {@link DecryptionException} on the first unwrap.
     *
     * @param provider the {@link Provider} to use; defaults to the JDK's default provider lookup
     * @return this builder
     */
    public Builder provider(Provider provider) {
      this.provider = Objects.requireNonNull(provider, "provider must not be null");
      return this;
    }

    /**
     * Builds the {@link JceDataKeyProvider}.
     *
     * @return a new {@link JceDataKeyProvider}
     * @throws IllegalStateException if a provider was set and cannot supply {@code AESWrap}
     */
    public JceDataKeyProvider build() {
      return new JceDataKeyProvider(currentKeyId, keks, random, provider);
    }
  }
}
