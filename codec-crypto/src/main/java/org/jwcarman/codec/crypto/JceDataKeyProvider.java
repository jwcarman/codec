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
import java.security.SecureRandom;
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
 * AESWrap needs no nonce, so wrapping the same DEK under the same KEK always produces the same
 * ciphertext, and its integrity check value (ICV) causes unwrap of a tampered or foreign-key blob
 * to fail rather than silently return garbage key material.
 *
 * <p>The KEK map <strong>is</strong> this provider's allowlist: {@link #allowsKeyId(String)} and
 * {@link #unwrap(String, byte[])} both consult it directly, so any key id absent from the map is
 * rejected — this satisfies the {@link DataKeyProvider} security contract without any separate
 * configuration. New data keys always wrap under the configured current KEK, while {@code unwrap}
 * accepts any KEK present in the map; this asymmetry is what lets a consumer rotate KEKs by adding
 * a new entry, pointing {@code currentKeyId} at it, and keeping the old entry only until every
 * message wrapped under it has been re-encrypted.
 *
 * <p>A constructor overload accepts a {@link SecureRandom} as a test seam; production code should
 * use the single-argument constructor, which defaults to a new {@link SecureRandom} instance.
 */
public final class JceDataKeyProvider implements DataKeyProvider {

  private static final String AES = "AES";
  private static final String WRAP_TRANSFORM = "AESWrap";
  private static final int DEK_LENGTH_BYTES = 32;

  private final String currentKeyId;
  private final Map<String, SecretKey> keks;
  private final SecureRandom random;

  /**
   * Creates a provider using a default {@link SecureRandom} instance.
   *
   * @param currentKeyId the id of the KEK, present in {@code keks}, used to wrap new data keys
   * @param keks the AES-256 key-encryption keys this provider trusts, keyed by id
   */
  public JceDataKeyProvider(String currentKeyId, Map<String, SecretKey> keks) {
    this(currentKeyId, keks, new SecureRandom());
  }

  /**
   * Creates a provider using the given {@link SecureRandom} for data-key generation.
   *
   * @param currentKeyId the id of the KEK, present in {@code keks}, used to wrap new data keys
   * @param keks the AES-256 key-encryption keys this provider trusts, keyed by id
   * @param random the source of randomness for generated data keys
   */
  public JceDataKeyProvider(String currentKeyId, Map<String, SecretKey> keks, SecureRandom random) {
    Objects.requireNonNull(currentKeyId, "currentKeyId must not be null");
    Objects.requireNonNull(keks, "keks must not be null");
    this.random = Objects.requireNonNull(random, "random must not be null");
    if (keks.isEmpty()) {
      throw new IllegalArgumentException("keks must not be empty");
    }
    if (!keks.containsKey(currentKeyId)) {
      throw new IllegalArgumentException("currentKeyId is not in the KEK map: " + currentKeyId);
    }
    keks.forEach(
        (id, key) -> {
          if (!AES.equals(key.getAlgorithm())) {
            throw new IllegalArgumentException(
                "KEK " + id + " is not an AES key: " + key.getAlgorithm());
          }
        });
    this.currentKeyId = currentKeyId;
    this.keks = Map.copyOf(keks);
  }

  @Override
  public DataKey newDataKey() {
    byte[] dekBytes = new byte[DEK_LENGTH_BYTES];
    random.nextBytes(dekBytes);
    SecretKey dek = new SecretKeySpec(dekBytes, AES);
    try {
      Cipher cipher = Cipher.getInstance(WRAP_TRANSFORM);
      cipher.init(Cipher.WRAP_MODE, keks.get(currentKeyId));
      return new DataKey(currentKeyId, dek, cipher.wrap(dek));
    } catch (GeneralSecurityException e) {
      throw new EncryptionException("Unable to wrap data key", e);
    }
  }

  @Override
  public SecretKey unwrap(String keyId, byte[] wrapped) {
    SecretKey kek = keks.get(keyId);
    if (kek == null) {
      throw DecryptionException.cryptographic(null);
    }
    try {
      Cipher cipher = Cipher.getInstance(WRAP_TRANSFORM);
      cipher.init(Cipher.UNWRAP_MODE, kek);
      return (SecretKey) cipher.unwrap(wrapped, AES, Cipher.SECRET_KEY);
    } catch (GeneralSecurityException e) {
      throw DecryptionException.cryptographic(e);
    }
  }

  @Override
  public boolean allowsKeyId(String keyId) {
    return keks.containsKey(keyId);
  }
}
