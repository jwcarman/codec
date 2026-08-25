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

/**
 * Default {@link DataKeyStrategy}: acquires a fresh data-encryption key per message.
 *
 * <p>Stateless, and therefore trivially thread-safe. Because every message gets its own DEK, this
 * strategy is nonce-misuse-immune by construction &mdash; there is no shared key under which two
 * nonces could ever collide. This makes it the right default for an in-process JCE provider and the
 * conservative choice for a remote KMS provider as well, at the cost of one network round trip per
 * encoded message when the provider is backed by a KMS. Consumers who need to amortize that cost
 * across multiple messages should use a bounded, caching strategy instead.
 */
public final class DirectDataKeyStrategy implements DataKeyStrategy {

  @Override
  public DataKey acquire(DataKeyProvider provider) {
    return provider.newDataKey();
  }
}
