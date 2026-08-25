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

import javax.crypto.SecretKey;

/**
 * SPI for envelope-encryption key management, implemented by consumers against an in-process key
 * store or a remote KMS (AWS KMS, HashiCorp Vault, GCP Cloud KMS, etc.).
 *
 * <p>{@code newDataKey} maps onto operations such as AWS KMS {@code GenerateDataKey} or Vault
 * {@code transit/datakey/plaintext}, which return a plaintext data-encryption key (DEK) and its
 * wrapped form in one round trip. GCP Cloud KMS has no equivalent data-key operation; its
 * documented envelope pattern is to generate the DEK locally and then call {@code Encrypt} to wrap
 * it, which is likewise a single round trip performed entirely inside {@code newDataKey()} &mdash;
 * implementors targeting GCP are using this SPI as intended. {@code unwrap} maps onto the
 * corresponding KMS decrypt operation.
 *
 * <h2>Security contract (normative)</h2>
 *
 * <p>{@code unwrap} receives a {@code keyId} and {@code wrapped} blob read from unauthenticated
 * ciphertext: the GCM tag cannot be verified until the DEK has been recovered, so at the time of
 * this call both arguments are attacker-controlled. Implementations MUST:
 *
 * <ul>
 *   <li>pass the supplied {@code keyId} to the KMS as the key restriction for the decrypt operation
 *       (e.g. the {@code KeyId} parameter of AWS KMS {@code Decrypt}) &mdash; never let the wrapped
 *       blob itself select which key is used to unwrap it; and
 *   <li>reject any {@code keyId} outside the set of key-encryption keys (KEKs) the application
 *       intends to trust.
 * </ul>
 *
 * <p>Without both of these, an attacker who knows the plaintext of any DEK wrapped under any KEK
 * the application is <em>permitted</em> to decrypt can forge messages that verify. Callers such as
 * {@code EnvelopeCodec} additionally enforce an allowlist before invoking {@code unwrap}; this
 * contract exists so that a provider used outside such a caller is not silently unsafe on its own.
 *
 * <h2>Caching contract</h2>
 *
 * <p>Implementations MAY cache {@code unwrap} results, keyed by {@code (keyId, hash(wrapped))}
 * &mdash; where the hash is collision-resistant (e.g. SHA-256) &mdash; or by the wrapped bytes
 * themselves, to avoid a network round trip per decode. Any such cache MUST be bounded in both
 * entries and time.
 *
 * <h2>Error contract</h2>
 *
 * <p>{@code unwrap} MUST throw {@link DecryptionException} only when the underlying KMS or JCE
 * layer has affirmatively rejected the blob as invalid (e.g. an integrity-check failure). Failures
 * that do not assert the blob is invalid &mdash; timeouts, throttling, credential expiry, or any
 * other availability failure &mdash; MUST instead propagate as other runtime exceptions, which
 * callers are expected to distinguish from an affirmative rejection (e.g. by wrapping them as
 * {@link KeyAccessException}). Conflating the two would turn a transient key-infrastructure outage
 * into data loss for a caller that quarantines or discards on {@code DecryptionException}.
 *
 * <h2>Thread-safety</h2>
 *
 * <p>Implementations MUST be safe for concurrent use: a single provider instance is shared across
 * both encode and decode paths, potentially from multiple threads at once.
 */
public interface DataKeyProvider {

  /**
   * Generates a fresh data-encryption key and returns it wrapped under this provider's current
   * key-encryption key.
   *
   * @return a new {@link DataKey} carrying the plaintext key and its wrapped form
   */
  DataKey newDataKey();

  /**
   * Recovers the plaintext data-encryption key from its wrapped form.
   *
   * <p>See the security, caching, and error contracts on this interface: both parameters are
   * attacker-controlled at the time of this call.
   *
   * @param keyId identifies which key-encryption key was used to wrap the data key
   * @param wrapped the wrapped data-encryption key bytes
   * @return the recovered plaintext key
   */
  SecretKey unwrap(String keyId, byte[] wrapped);

  /**
   * Returns whether this provider is willing to unwrap data keys wrapped under the given key id.
   *
   * <p>This is the provider's own admission check. The default implementation allows every key id;
   * implementations backed by a fixed set of trusted keys (such as a {@code Map} of KEKs) should
   * override this to reject any key id outside that set.
   *
   * @param keyId the key id to evaluate
   * @return {@code true} if this provider will unwrap data keys under {@code keyId}
   */
  default boolean allowsKeyId(String keyId) {
    return true;
  }
}
