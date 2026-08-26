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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BoundedDataKeyStrategyTest {

  private static final class CountingProvider implements DataKeyProvider {
    final AtomicInteger calls = new AtomicInteger();
    boolean failNext;

    @Override
    public DataKey newDataKey() {
      if (failNext) {
        throw new RuntimeException("provider down");
      }
      int n = calls.incrementAndGet();
      return new DataKey("kek-" + n, new SecretKeySpec(new byte[32], "AES"), new byte[] {(byte) n});
    }

    @Override
    public SecretKey unwrap(String keyId, byte[] wrapped) {
      return new SecretKeySpec(new byte[32], "AES");
    }
  }

  @Nested
  class Construction {
    @Test
    void rejects_zero_message_cap() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new BoundedDataKeyStrategy(0, Duration.ofMinutes(5)));
    }

    @Test
    void rejects_message_cap_over_two_to_the_24() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new BoundedDataKeyStrategy((1L << 24) + 1, Duration.ofMinutes(5)));
    }

    @Test
    void accepts_the_ceiling_exactly() {
      assertThat(new BoundedDataKeyStrategy(1L << 24, Duration.ofMinutes(5))).isNotNull();
    }

    @Test
    void rejects_non_positive_duration() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new BoundedDataKeyStrategy(10, Duration.ZERO));
    }

    @Test
    void rejects_a_negative_max_age() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new BoundedDataKeyStrategy(10, Duration.ofSeconds(-1)));
    }
  }

  @Nested
  class Rolling {
    @Test
    void reuses_the_cached_key_within_bounds() {
      CountingProvider provider = new CountingProvider();
      BoundedDataKeyStrategy strategy =
          new BoundedDataKeyStrategy(3, Duration.ofHours(1), () -> 0L);
      DataKey first = strategy.acquire(provider);
      assertThat(strategy.acquire(provider)).isEqualTo(first);
      assertThat(strategy.acquire(provider)).isEqualTo(first);
      assertThat(provider.calls).hasValue(1);
    }

    @Test
    void rolls_after_the_message_cap() {
      CountingProvider provider = new CountingProvider();
      BoundedDataKeyStrategy strategy =
          new BoundedDataKeyStrategy(2, Duration.ofHours(1), () -> 0L);
      DataKey first = strategy.acquire(provider);
      strategy.acquire(provider);
      DataKey third = strategy.acquire(provider);
      assertThat(third).isNotEqualTo(first);
      assertThat(provider.calls).hasValue(2);
    }

    @Test
    void rolls_after_the_duration_via_the_injected_ticker() {
      CountingProvider provider = new CountingProvider();
      AtomicLong nanos = new AtomicLong();
      BoundedDataKeyStrategy strategy =
          new BoundedDataKeyStrategy(1_000, Duration.ofSeconds(10), nanos::get);
      DataKey first = strategy.acquire(provider);
      nanos.set(Duration.ofSeconds(11).toNanos());
      DataKey second = strategy.acquire(provider);
      assertThat(second).isNotEqualTo(first);
      assertThat(provider.calls).hasValue(2);
    }

    @Test
    void a_cached_key_survives_the_nano_time_wraparound() {
      CountingProvider provider = new CountingProvider();
      AtomicLong nanos = new AtomicLong(Long.MAX_VALUE - 5);
      BoundedDataKeyStrategy strategy =
          new BoundedDataKeyStrategy(1_000, Duration.ofSeconds(10), nanos::get);
      DataKey before = strategy.acquire(provider);
      nanos.set(Long.MIN_VALUE + 100); // elapsed via subtraction: ~105ns, far under maxAge
      DataKey after = strategy.acquire(provider);
      assertThat(after).isEqualTo(before);
      assertThat(provider.calls).hasValue(1);
    }

    @Test
    void rolls_exactly_at_the_age_boundary() {
      CountingProvider provider = new CountingProvider();
      AtomicLong nanos = new AtomicLong(0L);
      BoundedDataKeyStrategy strategy =
          new BoundedDataKeyStrategy(1_000, Duration.ofSeconds(10), nanos::get);
      DataKey first = strategy.acquire(provider);
      nanos.set(Duration.ofSeconds(10).toNanos()); // elapsed == maxAge exactly: must not be usable
      DataKey second = strategy.acquire(provider);
      assertThat(second).isNotEqualTo(first);
      assertThat(provider.calls).hasValue(2);
    }

    @Test
    void elapsed_age_is_the_difference_between_ticker_reads_not_their_sum() {
      CountingProvider provider = new CountingProvider();
      AtomicLong nanos = new AtomicLong(5_000_000_000L);
      BoundedDataKeyStrategy strategy =
          new BoundedDataKeyStrategy(1_000, Duration.ofSeconds(10), nanos::get);
      DataKey first = strategy.acquire(provider);
      // Difference is 100ns (well under the 10s cap); the sum of the two ticker reads would exceed
      // it, so this distinguishes subtraction from addition.
      nanos.set(5_000_000_100L);
      DataKey second = strategy.acquire(provider);
      assertThat(second).isEqualTo(first);
      assertThat(provider.calls).hasValue(1);
    }

    @Test
    void provider_failure_during_roll_propagates_and_never_reuses_the_expired_key() {
      CountingProvider provider = new CountingProvider();
      BoundedDataKeyStrategy strategy =
          new BoundedDataKeyStrategy(1, Duration.ofHours(1), () -> 0L);
      strategy.acquire(provider);
      provider.failNext = true;
      org.assertj.core.api.Assertions.assertThatRuntimeException()
          .isThrownBy(() -> strategy.acquire(provider))
          .withMessage("provider down");
      provider.failNext = false;
      assertThat(strategy.acquire(provider).keyId()).isEqualTo("kek-2");
    }
  }

  @Nested
  class Concurrency {
    @Test
    void concurrent_acquires_never_exceed_the_message_cap_per_key() throws Exception {
      CountingProvider provider = new CountingProvider();
      int cap = 100;
      BoundedDataKeyStrategy strategy =
          new BoundedDataKeyStrategy(cap, Duration.ofHours(1), () -> 0L);
      int threads = 8;
      int perThread = 250;
      var results = new java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>();
      try (var executor = java.util.concurrent.Executors.newFixedThreadPool(threads)) {
        var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int t = 0; t < threads; t++) {
          futures.add(
              executor.submit(
                  () -> {
                    for (int i = 0; i < perThread; i++) {
                      DataKey dk = strategy.acquire(provider);
                      results
                          .computeIfAbsent(dk.keyId(), k -> new AtomicInteger())
                          .incrementAndGet();
                    }
                  }));
        }
        for (var f : futures) {
          f.get();
        }
      }
      assertThat(results.values())
          .allSatisfy(count -> assertThat(count.get()).isLessThanOrEqualTo(cap));
      assertThat(results.values().stream().mapToInt(AtomicInteger::get).sum())
          .isEqualTo(threads * perThread);
      assertThat(provider.calls.get()).isBetween(20, 20 + threads);
    }
  }
}
