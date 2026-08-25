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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Opt-in {@link DataKeyStrategy} that caches a data-encryption key (DEK) and rolls to a fresh one
 * after {@code maxMessages} messages or {@code maxAge} has elapsed, whichever comes first.
 *
 * <h2>Choosing a message cap</h2>
 *
 * <p>The message cap is validated to the range {@code [1, 2^24]}. At the {@code 2^24} ceiling, the
 * probability of a random-nonce collision under a single cached DEK is roughly {@code 2^-49}
 * &mdash; still well clear of the {@code 2^32}-random-nonce limit documented in NIST SP 800-38D for
 * a single key, but this class deliberately refuses to be configured all the way up to that cliff
 * edge. The recommended value is {@code 2^20} (roughly {@code 2^-57} collision probability), which
 * is a comfortable margin for the vast majority of consumers. A consumer who genuinely needs a
 * larger cap than {@code 2^24} allows should implement the {@link DataKeyStrategy} SPI directly
 * &mdash; that SPI exists precisely so this shipped class does not have to compromise on where it
 * draws the line.
 *
 * <p>The cap counts <em>messages</em>, not bytes. The assumption behind that choice is that message
 * sizes stay well below GCM's 64 GiB per-invocation plaintext limit; consumers encrypting
 * exceptionally large messages under a shared DEK should account for that limit themselves.
 *
 * <h2>Duration</h2>
 *
 * <p>Elapsed time is measured against a monotonic ticker injected as a {@link LongSupplier} of
 * nanoseconds. The production default is {@link System#nanoTime()}; a deterministic ticker may be
 * supplied for testing.
 *
 * <h2>VM snapshot / clone caveat</h2>
 *
 * <p>In environments where a running VM or container may be snapshotted and cloned (e.g. certain
 * serverless or sandboxed execution platforms), a clone can resume with an identical cached DEK and
 * duplicated {@link java.security.SecureRandom} state, which can cause nonces to repeat under that
 * shared key. Consumers operating in such environments should prefer {@link DirectDataKeyStrategy}
 * instead, or explicitly roll this strategy's key on resume from a snapshot.
 *
 * <h2>Key retirement</h2>
 *
 * <p>A retired DEK is simply released to the garbage collector when this strategy stops referencing
 * it &mdash; there is no {@code destroy()} call and no close hook. {@code SecretKeySpec} as shipped
 * by OpenJDK does not implement key destruction, and destroying a key that another thread may still
 * be using to encrypt would be a use-after-destroy race. In-flight operations that already hold a
 * retired {@link DataKey} remain valid; they are simply not handed out for new messages.
 *
 * <h2>Concurrency and rolling</h2>
 *
 * <p>Bounds are enforced with atomics rather than a lock held for the whole check-and-decrement.
 * Under contention, a roll may therefore happen one message earlier than strictly necessary, but it
 * is never late: no thread can observe more than {@code maxMessages} successful uses of a single
 * cached key. If the provider fails while rolling, the failure propagates to the caller and the
 * expired or exhausted key is never reused.
 */
public final class BoundedDataKeyStrategy implements DataKeyStrategy {

  private static final long MAX_MESSAGE_CAP = 1L << 24;

  private final long maxMessages;
  private final long maxAgeNanos;
  private final LongSupplier ticker;
  private final Object rollLock = new Object();
  private final AtomicReference<CachedKey> cached = new AtomicReference<>();

  private record CachedKey(DataKey key, long issuedAtNanos, AtomicLong remaining) {}

  /**
   * Creates a strategy using {@link System#nanoTime()} as the monotonic ticker.
   *
   * @param maxMessages the maximum number of messages a cached key may be used for, in {@code [1,
   *     2^24]}
   * @param maxAge the maximum age a cached key may reach before rolling; must be positive
   */
  public BoundedDataKeyStrategy(long maxMessages, Duration maxAge) {
    this(maxMessages, maxAge, System::nanoTime);
  }

  /**
   * Creates a strategy using the given ticker to measure elapsed time.
   *
   * @param maxMessages the maximum number of messages a cached key may be used for, in {@code [1,
   *     2^24]}
   * @param maxAge the maximum age a cached key may reach before rolling; must be positive
   * @param ticker a monotonic nanosecond-precision time source; production callers should use
   *     {@link System#nanoTime()}, tests may inject a deterministic ticker
   */
  public BoundedDataKeyStrategy(long maxMessages, Duration maxAge, LongSupplier ticker) {
    if (maxMessages < 1 || maxMessages > MAX_MESSAGE_CAP) {
      throw new IllegalArgumentException("maxMessages must be between 1 and 2^24: " + maxMessages);
    }
    Objects.requireNonNull(maxAge, "maxAge must not be null");
    if (maxAge.isZero() || maxAge.isNegative()) {
      throw new IllegalArgumentException("maxAge must be positive: " + maxAge);
    }
    this.maxMessages = maxMessages;
    this.maxAgeNanos = maxAge.toNanos();
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
  }

  @Override
  public DataKey acquire(DataKeyProvider provider) {
    CachedKey current = cached.get();
    if (current != null && usable(current)) {
      return current.key();
    }
    synchronized (rollLock) {
      current = cached.get();
      if (current != null && usable(current)) {
        return current.key();
      }
      DataKey fresh = provider.newDataKey();
      cached.set(new CachedKey(fresh, ticker.getAsLong(), new AtomicLong(maxMessages - 1)));
      return fresh;
    }
  }

  private boolean usable(CachedKey candidate) {
    return ticker.getAsLong() - candidate.issuedAtNanos() < maxAgeNanos
        && candidate.remaining().getAndDecrement() > 0;
  }
}
