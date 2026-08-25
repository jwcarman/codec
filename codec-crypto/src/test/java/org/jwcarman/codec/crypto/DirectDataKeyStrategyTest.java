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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DirectDataKeyStrategyTest {

  private static final class CountingProvider implements DataKeyProvider {
    final AtomicInteger calls = new AtomicInteger();

    @Override
    public DataKey newDataKey() {
      calls.incrementAndGet();
      return new DataKey("kek", new SecretKeySpec(new byte[32], "AES"), new byte[] {1});
    }

    @Override
    public SecretKey unwrap(String keyId, byte[] wrapped) {
      return new SecretKeySpec(new byte[32], "AES");
    }
  }

  @Test
  void acquires_a_fresh_data_key_per_call() {
    CountingProvider provider = new CountingProvider();
    DataKeyStrategy strategy = new DirectDataKeyStrategy();
    strategy.acquire(provider);
    strategy.acquire(provider);
    strategy.acquire(provider);
    assertThat(provider.calls).hasValue(3);
  }

  @Test
  void provider_allows_all_key_ids_by_default() {
    assertThat(new CountingProvider().allowsKeyId("anything")).isTrue();
  }
}
