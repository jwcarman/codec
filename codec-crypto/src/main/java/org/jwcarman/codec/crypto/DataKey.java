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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.SecretKey;

/**
 * A data-encryption key (DEK) acquired from a {@link DataKeyProvider}: the plaintext key material
 * used to encrypt or decrypt one message, paired with the id of the key-encryption key (KEK) that
 * wrapped it and the wrapped (ciphertext) form of the key itself.
 *
 * <p>{@code keyId} travels in cleartext in every message and identifies which KEK {@code wrapped}
 * was produced under; {@code key} is the plaintext DEK, held only in memory and never persisted or
 * transmitted; {@code wrapped} is the DEK encrypted under that KEK, carried alongside the
 * ciphertext so decode can recover {@code key} without a second key-generation call.
 *
 * <p>{@code equals}/{@code hashCode} deliberately compare only {@code keyId} and {@code wrapped},
 * ignoring {@code key}: two {@code DataKey} instances that wrap the same bytes under the same KEK
 * are the same data key for identity purposes, even if {@code key} is a distinct {@link SecretKey}
 * object (as, for example, two separate {@code unwrap} calls on the same wrapped bytes would
 * produce) — comparing raw key material would also risk timing side channels and adds no
 * information beyond what {@code wrapped} already encodes.
 *
 * @param keyId the id of the KEK that wrapped this key; 1..65535 UTF-8 bytes
 * @param key the plaintext data-encryption key
 * @param wrapped the wrapped (ciphertext) form of {@code key}; 1..65535 bytes, defensively cloned
 *     on construction and by {@link #wrapped()}
 */
public record DataKey(String keyId, SecretKey key, byte[] wrapped) {

  private static final int MAX_UINT16 = 65535;

  /**
   * Validates the fields and defensively clones {@code wrapped}.
   *
   * @throws NullPointerException if any field is {@code null}
   * @throws IllegalArgumentException if {@code keyId}'s UTF-8 byte length or {@code wrapped}'s
   *     length is outside {@code [1, 65535]} (the wire format's uint16 length fields)
   */
  public DataKey {
    Objects.requireNonNull(keyId, "keyId must not be null");
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(wrapped, "wrapped must not be null");
    int keyIdBytes = keyId.getBytes(StandardCharsets.UTF_8).length;
    if (keyIdBytes < 1 || keyIdBytes > MAX_UINT16) {
      throw new IllegalArgumentException("keyId must be 1..65535 UTF-8 bytes: " + keyIdBytes);
    }
    if (wrapped.length < 1 || wrapped.length > MAX_UINT16) {
      throw new IllegalArgumentException("wrapped must be 1..65535 bytes: " + wrapped.length);
    }
    wrapped = wrapped.clone();
  }

  /**
   * Returns the wrapped form of {@link #key()}.
   *
   * @return a defensive clone of the wrapped bytes; mutating the returned array does not affect
   *     this record
   */
  @Override
  public byte[] wrapped() {
    return wrapped.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DataKey other)) return false;
    return keyId.equals(other.keyId) && Arrays.equals(wrapped, other.wrapped);
  }

  /**
   * Combines {@code keyId} and {@code wrapped} as {@code 31 * keyId.hashCode() +
   * Arrays.hashCode(wrapped)}, consistent with {@link #equals(Object)} comparing the same two
   * fields.
   */
  @Override
  public int hashCode() {
    return 31 * keyId.hashCode() + Arrays.hashCode(wrapped);
  }

  /**
   * Returns a string containing {@link #keyId()} and the length of {@link #wrapped()}, never key
   * material.
   *
   * @return a diagnostic string safe to log
   */
  @Override
  public String toString() {
    return "DataKey[keyId=" + keyId + ", wrapped=" + wrapped.length + " bytes]";
  }
}
