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

public record DataKey(String keyId, SecretKey key, byte[] wrapped) {

  private static final int MAX_UINT16 = 65535;

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

  @Override
  public int hashCode() {
    return 31 * keyId.hashCode() + Arrays.hashCode(wrapped);
  }

  @Override
  public String toString() {
    return "DataKey[keyId=" + keyId + ", wrapped=" + wrapped.length + " bytes]";
  }
}
