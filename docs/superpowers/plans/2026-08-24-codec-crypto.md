# codec-crypto Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship `codec-crypto` — an AES-256-GCM envelope-encryption `Codec<byte[]>` transform with runtime key management, per spec 005.

**Architecture:** A new zero-external-dependency module. `DataKeyProvider` (SPI, with `allowsKeyId` admission default) supplies data keys; `DataKeyStrategy` (SPI) decides their lifecycle (fresh-per-message default, bounded caching opt-in); `EnvelopeCodec` owns the versioned wire format, nonce generation, and GCM calls, and composes through `Codec.andThen`.

**Tech Stack:** Java 25, JCE only (AES/GCM/NoPadding, AESWrap), JUnit 5 + AssertJ (NO Mockito — not on the classpath).

**Spec:** `docs/superpowers/specs/005-codec-crypto.md` — normative. If this plan and the spec disagree, STOP and report; do not pick one silently.

## Global Constraints

- `codec-crypto` depends on `codec-core` only; zero external compile dependencies. The ci profile's `dependency:analyze-only` (failOnWarning) and enforcer gates enforce this — never edit their allowlists.
- Verification command for every task: `./mvnw -Pci -B clean verify` (plain `verify` skips the gates and is NOT sufficient).
- No `@SuppressWarnings`. No star imports. Apache 2.0 license header on every new `.java` and `pom.xml` (copy the exact block from a neighbor file, e.g. `codec-gson/pom.xml` / `codec-core/src/main/java/org/jwcarman/codec/spi/Codec.java`).
- Tests: `@Nested` classes as capitalized phrases, `snake_case` sentence method names (house style: `codec-core/src/test/java/org/jwcarman/codec/spi/CodecTest.java`).
- Format before committing: `./mvnw -q spotless:apply`. Commit trailer: `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`. Never push.
- Package for all main code: `org.jwcarman.codec.crypto`. Test package identical.
- Wire constants (spec-normative, used across tasks): magic `0x4A,0x43`; version `0x01`; algorithm id `0x01` = AES-256-GCM; nonce 12 bytes; tag 128 bits (16 bytes); length fields unsigned uint16 big-endian, each ≥ 1; header = bytes `0 .. 19+k+w` inclusive; minimum total message length 38.
- The uniform cryptographic-failure message is exactly `"Unable to decrypt data"`.

---

### Task 1: Module scaffold

**Files:**
- Create: `codec-crypto/pom.xml`
- Modify: `pom.xml` (parent `<modules>`)
- Modify: `codec-bom/pom.xml` (managed entry)

**Interfaces:**
- Consumes: nothing.
- Produces: a building module every later task lands in; BOM manages `org.jwcarman.codec:codec-crypto`.

- [ ] **Step 1: Create the module pom**

`codec-crypto/pom.xml` — license header copied verbatim from `codec-gson/pom.xml`, then:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.jwcarman.codec</groupId>
        <artifactId>codec-parent</artifactId>
        <version>0.5.0-SNAPSHOT</version>
    </parent>

    <artifactId>codec-crypto</artifactId>
    <name>Codec Crypto</name>
    <description>Envelope-encryption transform for Codec — AES-256-GCM with pluggable key management</description>

    <properties>
        <module.name>org.jwcarman.codec.crypto</module.name>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.jwcarman.codec</groupId>
            <artifactId>codec-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Register the module in the parent**

In root `pom.xml`, `<modules>`: add `<module>codec-crypto</module>` after `<module>codec-core</module>`.

- [ ] **Step 3: Add the BOM entry**

In `codec-bom/pom.xml` `<dependencyManagement>`, after the `codec-core` entry:

```xml
            <dependency>
                <groupId>org.jwcarman.codec</groupId>
                <artifactId>codec-bom-placeholder-see-below</artifactId>
            </dependency>
```

Replace that placeholder with the real entry (shown fully so it is copy-pasteable):

```xml
            <dependency>
                <groupId>org.jwcarman.codec</groupId>
                <artifactId>codec-crypto</artifactId>
                <version>${project.version}</version>
            </dependency>
```

- [ ] **Step 4: Verify the reactor builds with the gates**

Run: `./mvnw -Pci -B clean verify`
Expected: BUILD SUCCESS, 10 modules, `codec-crypto` passing `dependency:analyze-only` and enforcer with no new allowlist entries.

- [ ] **Step 5: Commit**

```bash
git add codec-crypto/pom.xml pom.xml codec-bom/pom.xml
git commit -m "Scaffold codec-crypto module"
```

---

### Task 2: Exceptions

**Files:**
- Create: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/DecryptionException.java`
- Create: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/EncryptionException.java`
- Create: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/KeyAccessException.java`
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/ExceptionTaxonomyTest.java`

**Interfaces:**
- Produces: `DecryptionException extends IllegalArgumentException` with `DecryptionException(String message)`, `DecryptionException(String message, Throwable cause)`, and `static DecryptionException cryptographic(Throwable cause)` returning the uniform message `"Unable to decrypt data"`; `EncryptionException extends IllegalStateException` with `(String, Throwable)`; `KeyAccessException extends IllegalStateException` with `(String, Throwable)`.

- [ ] **Step 1: Write the failing test**

```java
package org.jwcarman.codec.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExceptionTaxonomyTest {

  @Test
  void decryption_exception_is_an_illegal_argument_exception() {
    assertThat(new DecryptionException("bad magic")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cryptographic_failures_share_one_uniform_message() {
    var cause = new RuntimeException("tag mismatch detail");
    DecryptionException e = DecryptionException.cryptographic(cause);
    assertThat(e).hasMessage("Unable to decrypt data").hasCause(cause);
  }

  @Test
  void key_access_exception_is_an_illegal_state_exception_preserving_cause() {
    var cause = new RuntimeException("kms timeout");
    assertThat(new KeyAccessException("key infrastructure unavailable", cause))
        .isInstanceOf(IllegalStateException.class)
        .hasCause(cause);
  }

  @Test
  void encryption_exception_is_an_illegal_state_exception_preserving_cause() {
    var cause = new RuntimeException("provider down");
    assertThat(new EncryptionException("unable to encrypt", cause))
        .isInstanceOf(IllegalStateException.class)
        .hasCause(cause);
  }
}
```

- [ ] **Step 2: Run to verify it fails** — `./mvnw -q -pl codec-crypto test` — Expected: compilation failure, classes missing.

- [ ] **Step 3: Implement**

`DecryptionException.java` (license header on every file; javadoc summarizing spec taxonomy):

```java
package org.jwcarman.codec.crypto;

public class DecryptionException extends IllegalArgumentException {

  private static final String CRYPTOGRAPHIC_FAILURE = "Unable to decrypt data";

  public DecryptionException(String message) {
    super(message);
  }

  public DecryptionException(String message, Throwable cause) {
    super(message, cause);
  }

  public static DecryptionException cryptographic(Throwable cause) {
    return new DecryptionException(CRYPTOGRAPHIC_FAILURE, cause);
  }
}
```

`EncryptionException.java`:

```java
package org.jwcarman.codec.crypto;

public class EncryptionException extends IllegalStateException {
  public EncryptionException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

`KeyAccessException.java`:

```java
package org.jwcarman.codec.crypto;

public class KeyAccessException extends IllegalStateException {
  public KeyAccessException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

Javadoc each class per the spec's error-handling section: `DecryptionException` = "this data is bad"; `KeyAccessException` = "the key infrastructure is unavailable — never quarantine data on this"; `EncryptionException` = encode-side failure. Include the timing-side-channel scope sentence on `DecryptionException` (indistinguishability is exception content only).

- [ ] **Step 4: Run** — `./mvnw -q -pl codec-crypto test` — Expected: PASS.

- [ ] **Step 5: Commit** — `git add codec-crypto && git commit -m "Add codec-crypto exception taxonomy"`

---

### Task 3: DataKey record

**Files:**
- Create: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/DataKey.java`
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/DataKeyTest.java`

**Interfaces:**
- Produces: `public record DataKey(String keyId, SecretKey key, byte[] wrapped)` — validated, defensively copied, content-equal on `keyId`+`wrapped` only, `toString` never prints key material.

- [ ] **Step 1: Write the failing test**

```java
package org.jwcarman.codec.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DataKeyTest {

  private static final SecretKey KEY = new SecretKeySpec(new byte[32], "AES");

  @Nested
  class Validation {
    @Test
    void rejects_null_key_id() {
      assertThatNullPointerException()
          .isThrownBy(() -> new DataKey(null, KEY, new byte[] {1}));
    }

    @Test
    void rejects_empty_key_id() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new DataKey("", KEY, new byte[] {1}));
    }

    @Test
    void rejects_key_id_longer_than_uint16_in_utf8_bytes() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new DataKey("x".repeat(65536), KEY, new byte[] {1}));
    }

    @Test
    void rejects_empty_wrapped() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new DataKey("kek", KEY, new byte[0]));
    }

    @Test
    void rejects_wrapped_longer_than_uint16() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new DataKey("kek", KEY, new byte[65536]));
    }

    @Test
    void key_id_length_is_measured_in_utf8_bytes_not_chars() {
      String multibyte = "é".repeat(40000); // 80000 UTF-8 bytes > 65535
      assertThat(multibyte.length()).isLessThan(65536);
      assertThat(multibyte.getBytes(UTF_8).length).isGreaterThan(65535);
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new DataKey(multibyte, KEY, new byte[] {1}));
    }
  }

  @Nested
  class Defensive_copies {
    @Test
    void mutating_the_constructor_argument_does_not_affect_the_record() {
      byte[] wrapped = {1, 2, 3};
      DataKey dk = new DataKey("kek", KEY, wrapped);
      wrapped[0] = 99;
      assertThat(dk.wrapped()).containsExactly(1, 2, 3);
    }

    @Test
    void mutating_the_accessor_result_does_not_affect_the_record() {
      DataKey dk = new DataKey("kek", KEY, new byte[] {1, 2, 3});
      dk.wrapped()[0] = 99;
      assertThat(dk.wrapped()).containsExactly(1, 2, 3);
    }
  }

  @Nested
  class Equality_and_printing {
    @Test
    void equal_on_key_id_and_wrapped_content_ignoring_key() {
      SecretKey other = new SecretKeySpec(new byte[] {9, 9, 9, 9, 9, 9, 9, 9,
          9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9}, "AES");
      assertThat(new DataKey("kek", KEY, new byte[] {1, 2}))
          .isEqualTo(new DataKey("kek", other, new byte[] {1, 2}))
          .hasSameHashCodeAs(new DataKey("kek", other, new byte[] {1, 2}));
      assertThat(new DataKey("kek", KEY, new byte[] {1, 2}))
          .isNotEqualTo(new DataKey("kek", KEY, new byte[] {1, 3}));
    }

    @Test
    void to_string_reveals_key_id_and_wrapped_length_but_no_key_material() {
      DataKey dk = new DataKey("kek", new SecretKeySpec("supersecretkey--supersecretkey--".getBytes(UTF_8), "AES"), new byte[] {1, 2, 3});
      assertThat(dk.toString()).contains("kek").contains("3").doesNotContain("supersecret");
    }
  }
}
```

- [ ] **Step 2: Run to verify it fails** — `./mvnw -q -pl codec-crypto test` — Expected: compilation failure.

- [ ] **Step 3: Implement**

```java
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
      throw new IllegalArgumentException(
          "keyId must be 1..65535 UTF-8 bytes: " + keyIdBytes);
    }
    if (wrapped.length < 1 || wrapped.length > MAX_UINT16) {
      throw new IllegalArgumentException(
          "wrapped must be 1..65535 bytes: " + wrapped.length);
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
```

- [ ] **Step 4: Run** — `./mvnw -q -pl codec-crypto test` — Expected: PASS.

- [ ] **Step 5: Commit** — `git add codec-crypto && git commit -m "Add DataKey record with validation and defensive copies"`

---

### Task 4: SPI interfaces — DataKeyProvider and DataKeyStrategy, plus DirectDataKeyStrategy

**Files:**
- Create: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/DataKeyProvider.java`
- Create: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/DataKeyStrategy.java`
- Create: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/DirectDataKeyStrategy.java`
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/DirectDataKeyStrategyTest.java`

**Interfaces:**
- Consumes: `DataKey` (Task 3).
- Produces: `DataKeyProvider` with `DataKey newDataKey()`, `SecretKey unwrap(String keyId, byte[] wrapped)`, `default boolean allowsKeyId(String keyId) { return true; }`; `DataKeyStrategy` with `DataKey acquire(DataKeyProvider provider)`; `DirectDataKeyStrategy` (public final, no-arg constructor).

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run to verify it fails** — `./mvnw -q -pl codec-crypto test` — Expected: compilation failure.

- [ ] **Step 3: Implement**

`DataKeyProvider.java` — the javadoc IS the deliverable here; it must carry, verbatim in intent, the spec's normative security contract (unwrap receives attacker-controlled bytes; implementations MUST pass keyId to the KMS as the key restriction and MUST reject keyIds outside the trusted set), the caching contract (MAY cache unwrap results keyed by `(keyId, SHA-256(wrapped))` or the wrapped bytes; cache MUST be bounded in entries and time), the error contract (throw `DecryptionException` only for affirmative rejection of the blob; let availability failures propagate as other runtime exceptions), the GCP note (no data-key API; generate-locally-then-Encrypt inside `newDataKey()` is the intended use), and the thread-safety requirement:

```java
package org.jwcarman.codec.crypto;

import javax.crypto.SecretKey;

public interface DataKeyProvider {

  DataKey newDataKey();

  SecretKey unwrap(String keyId, byte[] wrapped);

  default boolean allowsKeyId(String keyId) {
    return true;
  }
}
```

`DataKeyStrategy.java` (javadoc: pure lifecycle policy; implementations decide fresh-vs-cached; must be thread-safe):

```java
package org.jwcarman.codec.crypto;

public interface DataKeyStrategy {

  DataKey acquire(DataKeyProvider provider);
}
```

`DirectDataKeyStrategy.java` (javadoc: the default; one DEK per message — nonce-misuse-immune by construction; the conservative choice for KMS providers, with the per-message network cost stated):

```java
package org.jwcarman.codec.crypto;

public final class DirectDataKeyStrategy implements DataKeyStrategy {

  @Override
  public DataKey acquire(DataKeyProvider provider) {
    return provider.newDataKey();
  }
}
```

- [ ] **Step 4: Run** — `./mvnw -q -pl codec-crypto test` — Expected: PASS.

- [ ] **Step 5: Commit** — `git add codec-crypto && git commit -m "Add DataKeyProvider and DataKeyStrategy SPIs with direct strategy"`

---

### Task 5: BoundedDataKeyStrategy

**Files:**
- Create: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/BoundedDataKeyStrategy.java`
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/BoundedDataKeyStrategyTest.java`

**Interfaces:**
- Consumes: `DataKeyProvider`, `DataKeyStrategy`, `DataKey`.
- Produces: `public final class BoundedDataKeyStrategy implements DataKeyStrategy` with constructors `BoundedDataKeyStrategy(long maxMessages, Duration maxAge)` and `BoundedDataKeyStrategy(long maxMessages, Duration maxAge, LongSupplier ticker)`; message cap valid range `[1, 1L << 24]`.

- [ ] **Step 1: Write the failing test**

```java
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
          futures.add(executor.submit(() -> {
            for (int i = 0; i < perThread; i++) {
              DataKey dk = strategy.acquire(provider);
              results.computeIfAbsent(dk.keyId(), k -> new AtomicInteger()).incrementAndGet();
            }
          }));
        }
        for (var f : futures) {
          f.get();
        }
      }
      assertThat(results.values()).allSatisfy(count -> assertThat(count.get()).isLessThanOrEqualTo(cap));
      assertThat(results.values().stream().mapToInt(AtomicInteger::get).sum())
          .isEqualTo(threads * perThread);
    }
  }
}
```

- [ ] **Step 2: Run to verify it fails** — `./mvnw -q -pl codec-crypto test` — Expected: compilation failure.

- [ ] **Step 3: Implement**

Javadoc must carry: recommended cap 2^20 (ceiling 2^24, why: collision ~2^-49 vs NIST 2^32 limit — the shipped class refuses the cliff edge; implement the strategy SPI if you truly need more); the bound counts messages, not bytes; the VM-snapshot/clone caveat (prefer `DirectDataKeyStrategy` or roll on resume); retired DEKs are released to GC, in-flight users unaffected; per-message-early-roll is possible under contention, never late.

```java
package org.jwcarman.codec.crypto;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class BoundedDataKeyStrategy implements DataKeyStrategy {

  private static final long MAX_MESSAGE_CAP = 1L << 24;

  private final long maxMessages;
  private final long maxAgeNanos;
  private final LongSupplier ticker;
  private final Object rollLock = new Object();
  private volatile CachedKey cached;

  private record CachedKey(DataKey key, long expiresAtNanos, AtomicLong remaining) {}

  public BoundedDataKeyStrategy(long maxMessages, Duration maxAge) {
    this(maxMessages, maxAge, System::nanoTime);
  }

  public BoundedDataKeyStrategy(long maxMessages, Duration maxAge, LongSupplier ticker) {
    if (maxMessages < 1 || maxMessages > MAX_MESSAGE_CAP) {
      throw new IllegalArgumentException(
          "maxMessages must be between 1 and 2^24: " + maxMessages);
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
    CachedKey current = cached;
    if (current != null && usable(current)) {
      return current.key();
    }
    synchronized (rollLock) {
      current = cached;
      if (current != null && usable(current)) {
        return current.key();
      }
      DataKey fresh = provider.newDataKey();
      cached = new CachedKey(fresh, ticker.getAsLong() + maxAgeNanos, new AtomicLong(maxMessages - 1));
      return fresh;
    }
  }

  private boolean usable(CachedKey candidate) {
    return ticker.getAsLong() < candidate.expiresAtNanos()
        && candidate.remaining().getAndDecrement() > 0;
  }
}
```

Note for the implementer: `usable` consumes one permit on success; the freshly rolled key has `maxMessages - 1` remaining because the roll itself hands out the first use. A failed provider call inside the lock leaves `cached` untouched, but the expired/exhausted entry is never returned because `usable` already rejected it.

- [ ] **Step 4: Run** — `./mvnw -q -pl codec-crypto test` — Expected: PASS, including the concurrency test.

- [ ] **Step 5: Commit** — `git add codec-crypto && git commit -m "Add BoundedDataKeyStrategy with enforced roll bounds"`

---

### Task 6: JceDataKeyProvider

**Files:**
- Create: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/JceDataKeyProvider.java`
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/JceDataKeyProviderTest.java`

**Interfaces:**
- Consumes: `DataKeyProvider`, `DataKey`, `DecryptionException`.
- Produces: `public final class JceDataKeyProvider implements DataKeyProvider` with constructors `JceDataKeyProvider(String currentKeyId, Map<String, SecretKey> keks)` and `JceDataKeyProvider(String currentKeyId, Map<String, SecretKey> keks, SecureRandom random)`; overrides `allowsKeyId` from the map.

- [ ] **Step 1: Write the failing test**

```java
package org.jwcarman.codec.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.security.SecureRandom;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JceDataKeyProviderTest {

  private static SecretKey aesKey(byte fill) {
    byte[] bytes = new byte[32];
    java.util.Arrays.fill(bytes, fill);
    return new SecretKeySpec(bytes, "AES");
  }

  @Nested
  class Construction {
    @Test
    void rejects_a_current_key_id_absent_from_the_map() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new JceDataKeyProvider("missing", Map.of("kek", aesKey((byte) 1))));
    }

    @Test
    void rejects_an_empty_kek_map() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new JceDataKeyProvider("kek", Map.of()));
    }

    @Test
    void rejects_a_non_aes_kek() {
      SecretKey hmac = new SecretKeySpec(new byte[32], "HmacSHA256");
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new JceDataKeyProvider("kek", Map.of("kek", hmac)));
    }
  }

  @Nested
  class Round_tripping {
    @Test
    void a_generated_data_key_unwraps_to_the_same_key_material() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      DataKey dk = provider.newDataKey();
      SecretKey unwrapped = provider.unwrap(dk.keyId(), dk.wrapped());
      assertThat(unwrapped.getEncoded()).isEqualTo(dk.key().getEncoded());
      assertThat(dk.keyId()).isEqualTo("kek");
    }

    @Test
    void unwrap_resolves_historical_keks_while_wrapping_under_the_current_one() {
      JceDataKeyProvider old = new JceDataKeyProvider("old", Map.of("old", aesKey((byte) 1)));
      DataKey wrappedUnderOld = old.newDataKey();
      JceDataKeyProvider rotated = new JceDataKeyProvider(
          "new", Map.of("old", aesKey((byte) 1), "new", aesKey((byte) 2)));
      assertThat(rotated.newDataKey().keyId()).isEqualTo("new");
      assertThat(rotated.unwrap("old", wrappedUnderOld.wrapped()).getEncoded())
          .isEqualTo(wrappedUnderOld.key().getEncoded());
    }

    @Test
    void generated_deks_are_256_bit_and_distinct() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      DataKey a = provider.newDataKey();
      DataKey b = provider.newDataKey();
      assertThat(a.key().getEncoded()).hasSize(32);
      assertThat(a.key().getEncoded()).isNotEqualTo(b.key().getEncoded());
    }

    @Test
    void an_injected_secure_random_makes_dek_generation_deterministic() {
      JceDataKeyProvider a =
          new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)), fixedRandom());
      JceDataKeyProvider b =
          new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)), fixedRandom());
      assertThat(a.newDataKey().key().getEncoded()).isEqualTo(b.newDataKey().key().getEncoded());
    }

    private static SecureRandom fixedRandom() {
      return new SecureRandom() {
        private byte next = 0;

        @Override
        public void nextBytes(byte[] bytes) {
          for (int i = 0; i < bytes.length; i++) {
            bytes[i] = next++;
          }
        }
      };
    }
  }

  @Nested
  class Admission_and_rejection {
    @Test
    void allows_only_key_ids_in_the_map() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      assertThat(provider.allowsKeyId("kek")).isTrue();
      assertThat(provider.allowsKeyId("other")).isFalse();
    }

    @Test
    void unwrap_of_an_unknown_key_id_is_a_decryption_exception_with_the_uniform_message() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> provider.unwrap("other", new byte[] {1, 2, 3, 4, 5, 6, 7, 8}))
          .withMessage("Unable to decrypt data");
    }

    @Test
    void unwrap_of_a_tampered_blob_is_a_decryption_exception_with_the_uniform_message() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      byte[] wrapped = provider.newDataKey().wrapped();
      wrapped[0] ^= 1;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> provider.unwrap("kek", wrapped))
          .withMessage("Unable to decrypt data");
    }
  }
}
```

- [ ] **Step 2: Run to verify it fails** — `./mvnw -q -pl codec-crypto test` — Expected: compilation failure.

- [ ] **Step 3: Implement**

Javadoc: the KEK map IS the allowlist (satisfies the SPI security contract); AESWrap (RFC 3394) is deterministic and integrity-checked; multiple KEKs give JCE users rotation (wrap under current, unwrap any mapped KEK).

```java
package org.jwcarman.codec.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class JceDataKeyProvider implements DataKeyProvider {

  private static final String AES = "AES";
  private static final String WRAP_TRANSFORM = "AESWrap";
  private static final int DEK_LENGTH_BYTES = 32;

  private final String currentKeyId;
  private final Map<String, SecretKey> keks;
  private final SecureRandom random;

  public JceDataKeyProvider(String currentKeyId, Map<String, SecretKey> keks) {
    this(currentKeyId, keks, new SecureRandom());
  }

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
    keks.forEach((id, key) -> {
      if (!AES.equals(key.getAlgorithm())) {
        throw new IllegalArgumentException("KEK " + id + " is not an AES key: " + key.getAlgorithm());
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
```

Note: `DecryptionException.cryptographic(null)` requires the Task 2 factory to accept a null cause — `IllegalArgumentException(String, null)` is legal; no change needed.

- [ ] **Step 4: Run** — `./mvnw -q -pl codec-crypto test` — Expected: PASS.

- [ ] **Step 5: Commit** — `git add codec-crypto && git commit -m "Add JceDataKeyProvider with AESWrap and map-based admission"`

---

### Task 7: EnvelopeCodec — builder and encode path

**Files:**
- Create: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/EnvelopeCodec.java`
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/EnvelopeCodecEncodeTest.java`

**Interfaces:**
- Consumes: everything above; `org.jwcarman.codec.spi.Codec`.
- Produces: `public final class EnvelopeCodec implements Codec<byte[]>`; `public static Builder builder(DataKeyProvider provider)`; `Builder` methods `strategy(DataKeyStrategy)`, `aad(byte[])`, `allowedKeyIds(Predicate<String>)`, `secureRandom(SecureRandom)`, `build()`. Wire layout exactly per Global Constraints. `decode` lands in Task 8 — in this task it exists but is implemented completely per Task 8's code (both tasks touch one class; Task 7 may leave `decode` throwing `new UnsupportedOperationException("implemented in the decode task")` ONLY if Task 8 immediately follows in the same plan run — it does).

- [ ] **Step 1: Write the failing test**

```java
package org.jwcarman.codec.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EnvelopeCodecEncodeTest {

  private static JceDataKeyProvider provider() {
    byte[] kek = new byte[32];
    java.util.Arrays.fill(kek, (byte) 7);
    return new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES")));
  }

  @Nested
  class Builder_validation {
    @Test
    void rejects_an_empty_aad() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).aad(new byte[0]).build());
    }
  }

  @Nested
  class Wire_layout {
    @Test
    void encoded_output_carries_the_documented_header() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode("hello".getBytes(UTF_8));

      assertThat(message[0]).isEqualTo((byte) 0x4A);
      assertThat(message[1]).isEqualTo((byte) 0x43);
      assertThat(message[2]).isEqualTo((byte) 0x01); // version
      assertThat(message[3]).isEqualTo((byte) 0x01); // AES-256-GCM

      int k = ((message[4] & 0xFF) << 8) | (message[5] & 0xFF);
      assertThat(new String(message, 6, k, UTF_8)).isEqualTo("kek");

      int wOffset = 6 + k;
      int w = ((message[wOffset] & 0xFF) << 8) | (message[wOffset + 1] & 0xFF);
      assertThat(w).isEqualTo(40); // AESWrap of a 32-byte DEK

      // header(20 + k + w) + ciphertext(5) + tag(16)
      assertThat(message).hasSize(20 + k + w + 5 + 16);
    }

    @Test
    void empty_plaintext_encodes_to_header_plus_tag_only() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[0]);
      int k = ((message[4] & 0xFF) << 8) | (message[5] & 0xFF);
      int wOffset = 6 + k;
      int w = ((message[wOffset] & 0xFF) << 8) | (message[wOffset + 1] & 0xFF);
      assertThat(message).hasSize(20 + k + w + 16);
    }

    @Test
    void two_encodes_of_the_same_plaintext_differ() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] plaintext = "same".getBytes(UTF_8);
      assertThat(codec.encode(plaintext)).isNotEqualTo(codec.encode(plaintext));
    }
  }

  @Nested
  class Encode_failures {
    @Test
    void a_provider_failure_surfaces_as_an_encryption_exception() {
      DataKeyProvider failing = new DataKeyProvider() {
        @Override
        public DataKey newDataKey() {
          throw new RuntimeException("kms down");
        }

        @Override
        public javax.crypto.SecretKey unwrap(String keyId, byte[] wrapped) {
          throw new UnsupportedOperationException();
        }
      };
      assertThatExceptionOfType(EncryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(failing).build().encode(new byte[] {1}))
          .withCauseInstanceOf(RuntimeException.class);
    }
  }
}
```

- [ ] **Step 2: Run to verify it fails** — `./mvnw -q -pl codec-crypto test` — Expected: compilation failure.

- [ ] **Step 3: Implement**

```java
package org.jwcarman.codec.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.Predicate;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.jwcarman.codec.spi.Codec;

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

  private final DataKeyProvider provider;
  private final DataKeyStrategy strategy;
  private final byte[] aad;
  private final Predicate<String> allowedKeyIds;
  private final SecureRandom random;

  private EnvelopeCodec(Builder builder) {
    this.provider = builder.provider;
    this.strategy = builder.strategy;
    this.aad = builder.aad;
    this.allowedKeyIds = builder.allowedKeyIds;
    this.random = builder.random;
  }

  public static Builder builder(DataKeyProvider provider) {
    return new Builder(provider);
  }

  @Override
  public byte[] encode(byte[] value) {
    Objects.requireNonNull(value, "value must not be null");
    DataKey dataKey;
    try {
      dataKey = strategy.acquire(provider);
    } catch (RuntimeException e) {
      throw new EncryptionException("Unable to acquire data key", e);
    }
    byte[] keyIdBytes = dataKey.keyId().getBytes(StandardCharsets.UTF_8);
    byte[] wrapped = dataKey.wrapped();
    byte[] nonce = new byte[NONCE_LENGTH];
    random.nextBytes(nonce);

    int headerLength = FIXED_HEADER_LENGTH + keyIdBytes.length + wrapped.length;
    byte[] message = new byte[headerLength + value.length + TAG_LENGTH_BYTES];
    ByteBuffer header = ByteBuffer.wrap(message, 0, headerLength);
    header.put(MAGIC_0).put(MAGIC_1).put(FORMAT_VERSION).put(ALGORITHM_AES_256_GCM);
    header.putShort((short) keyIdBytes.length).put(keyIdBytes);
    header.putShort((short) wrapped.length).put(wrapped);
    header.put(nonce);

    try {
      Cipher cipher = Cipher.getInstance(GCM_TRANSFORM);
      cipher.init(Cipher.ENCRYPT_MODE, dataKey.key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
      cipher.updateAAD(message, 0, headerLength);
      if (aad != null) {
        cipher.updateAAD(aad);
      }
      cipher.doFinal(value, 0, value.length, message, headerLength);
    } catch (GeneralSecurityException e) {
      throw new EncryptionException("Unable to encrypt data", e);
    }
    return message;
  }

  @Override
  public byte[] decode(byte[] bytes) {
    throw new UnsupportedOperationException("implemented in the decode task");
  }

  public static final class Builder {

    private final DataKeyProvider provider;
    private DataKeyStrategy strategy = new DirectDataKeyStrategy();
    private byte[] aad;
    private Predicate<String> allowedKeyIds;
    private SecureRandom random = new SecureRandom();

    private Builder(DataKeyProvider provider) {
      this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    public Builder strategy(DataKeyStrategy strategy) {
      this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
      return this;
    }

    public Builder aad(byte[] aad) {
      Objects.requireNonNull(aad, "aad must not be null");
      if (aad.length == 0) {
        throw new IllegalArgumentException(
            "aad must not be empty: empty and absent AAD are cryptographically identical");
      }
      this.aad = aad.clone();
      return this;
    }

    public Builder allowedKeyIds(Predicate<String> allowedKeyIds) {
      this.allowedKeyIds = Objects.requireNonNull(allowedKeyIds, "allowedKeyIds must not be null");
      return this;
    }

    public Builder secureRandom(SecureRandom random) {
      this.random = Objects.requireNonNull(random, "random must not be null");
      return this;
    }

    public EnvelopeCodec build() {
      return new EnvelopeCodec(this);
    }
  }
}
```

Javadoc requirements for the class (all spec "docs requirement" items that belong on the API): compress-then-encrypt ordering with the CRIME/BREACH caveat; the fixed-AAD ciphertext-substitution limitation and mitigations; the per-message KMS cost of the default strategy; keyIds are visible in ciphertext — no secrets in keyIds; the wire format is proprietary to this library and permanent.

- [ ] **Step 4: Run** — `./mvnw -q -pl codec-crypto test` — Expected: PASS (decode tests do not exist yet).

- [ ] **Step 5: Commit** — `git add codec-crypto && git commit -m "Add EnvelopeCodec builder and encode path"`

---

### Task 8: EnvelopeCodec — decode path, validation order, admission

**Files:**
- Modify: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/EnvelopeCodec.java` (replace the `decode` stub)
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/EnvelopeCodecDecodeTest.java`

**Interfaces:**
- Consumes: Task 7's class and constants verbatim.
- Produces: working `decode(byte[])` implementing the spec's 7-step validation order; admission = builder predicate if set, else `provider.allowsKeyId`.

- [ ] **Step 1: Write the failing test**

```java
package org.jwcarman.codec.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EnvelopeCodecDecodeTest {

  private static JceDataKeyProvider provider() {
    byte[] kek = new byte[32];
    java.util.Arrays.fill(kek, (byte) 7);
    return new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES")));
  }

  @Nested
  class Round_tripping {
    @Test
    void decode_recovers_the_plaintext() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] plaintext = "round trip".getBytes(UTF_8);
      assertThat(codec.decode(codec.encode(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void empty_plaintext_round_trips() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      assertThat(codec.decode(codec.encode(new byte[0]))).isEmpty();
    }

    @Test
    void aad_bound_ciphertext_round_trips_with_the_same_aad() {
      EnvelopeCodec codec =
          EnvelopeCodec.builder(provider()).aad("tenant-1".getBytes(UTF_8)).build();
      byte[] plaintext = "bound".getBytes(UTF_8);
      assertThat(codec.decode(codec.encode(plaintext))).isEqualTo(plaintext);
    }
  }

  @Nested
  class Structural_rejection {
    @Test
    void rejects_input_shorter_than_the_minimum_message_length() {
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(new byte[37]));
    }

    @Test
    void rejects_bad_magic_with_a_structural_message() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      message[0] = 0x00;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(message))
          .withMessageContaining("magic");
    }

    @Test
    void rejects_an_unknown_version() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      message[2] = 0x02;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(message))
          .withMessageContaining("version");
    }

    @Test
    void rejects_an_unknown_algorithm() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      message[3] = 0x7F;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(message))
          .withMessageContaining("algorithm");
    }

    @Test
    void rejects_a_key_id_length_that_overruns_the_buffer() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      message[4] = (byte) 0xFF;
      message[5] = (byte) 0xFF;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(message));
    }
  }

  @Nested
  class Admission {
    @Test
    void a_key_id_rejected_by_the_builder_predicate_never_reaches_unwrap() {
      AtomicInteger unwraps = new AtomicInteger();
      JceDataKeyProvider real = provider();
      DataKeyProvider counting = new DataKeyProvider() {
        @Override
        public DataKey newDataKey() {
          return real.newDataKey();
        }

        @Override
        public SecretKey unwrap(String keyId, byte[] wrapped) {
          unwraps.incrementAndGet();
          return real.unwrap(keyId, wrapped);
        }
      };
      byte[] message = EnvelopeCodec.builder(counting).build().encode(new byte[] {1});
      EnvelopeCodec restrictive =
          EnvelopeCodec.builder(counting).allowedKeyIds(id -> false).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> restrictive.decode(message));
      assertThat(unwraps).hasValue(0);
    }

    @Test
    void without_a_predicate_admission_falls_to_the_providers_allows_key_id() {
      AtomicInteger unwraps = new AtomicInteger();
      JceDataKeyProvider real = provider();
      DataKeyProvider denying = new DataKeyProvider() {
        @Override
        public DataKey newDataKey() {
          return real.newDataKey();
        }

        @Override
        public SecretKey unwrap(String keyId, byte[] wrapped) {
          unwraps.incrementAndGet();
          return real.unwrap(keyId, wrapped);
        }

        @Override
        public boolean allowsKeyId(String keyId) {
          return false;
        }
      };
      byte[] message = EnvelopeCodec.builder(denying).build().encode(new byte[] {1});
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(denying).build().decode(message));
      assertThat(unwraps).hasValue(0);
    }

    @Test
    void the_builder_predicate_overrides_a_permissive_provider() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      EnvelopeCodec codec =
          EnvelopeCodec.builder(provider()).allowedKeyIds("kek"::equals).build();
      assertThat(codec.decode(message)).containsExactly(1);
    }
  }

  @Nested
  class Error_taxonomy {
    @Test
    void a_provider_availability_failure_is_a_key_access_exception_not_decryption() {
      JceDataKeyProvider real = provider();
      DataKeyProvider flaky = new DataKeyProvider() {
        @Override
        public DataKey newDataKey() {
          return real.newDataKey();
        }

        @Override
        public SecretKey unwrap(String keyId, byte[] wrapped) {
          throw new RuntimeException("kms timeout");
        }
      };
      byte[] message = EnvelopeCodec.builder(flaky).build().encode(new byte[] {1});
      assertThatExceptionOfType(KeyAccessException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(flaky).build().decode(message))
          .withCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void a_provider_rejection_stays_a_decryption_exception() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode(new byte[] {1});
      // decode with a provider holding a DIFFERENT kek: unwrap fails cryptographically
      byte[] otherKek = new byte[32];
      java.util.Arrays.fill(otherKek, (byte) 9);
      JceDataKeyProvider wrongKeys =
          new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(otherKek, "AES")));
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(wrongKeys).build().decode(message))
          .withMessage("Unable to decrypt data");
    }
  }
}
```

- [ ] **Step 2: Run to verify it fails** — `./mvnw -q -pl codec-crypto test` — Expected: decode tests fail on the `UnsupportedOperationException` stub.

- [ ] **Step 3: Implement decode** (replace the stub in `EnvelopeCodec`):

```java
  @Override
  public byte[] decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    if (bytes.length < MIN_MESSAGE_LENGTH) {
      throw new DecryptionException("message too short: " + bytes.length + " bytes");
    }
    if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) {
      throw new DecryptionException("bad magic: not an envelope");
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
    if (!admits(keyId)) {
      throw new DecryptionException("keyId is not allowed: " + keyId);
    }
    byte[] wrapped = java.util.Arrays.copyOfRange(bytes, wrappedOffset + 2, wrappedOffset + 2 + wrappedLength);
    SecretKey dek = unwrapDataKey(keyId, wrapped);
    byte[] nonce = java.util.Arrays.copyOfRange(bytes, headerLength - NONCE_LENGTH, headerLength);
    try {
      Cipher cipher = Cipher.getInstance(GCM_TRANSFORM);
      cipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
      cipher.updateAAD(bytes, 0, headerLength);
      if (aad != null) {
        cipher.updateAAD(aad);
      }
      return cipher.doFinal(bytes, headerLength, bytes.length - headerLength);
    } catch (GeneralSecurityException e) {
      throw DecryptionException.cryptographic(e);
    }
  }

  private boolean admits(String keyId) {
    return allowedKeyIds != null ? allowedKeyIds.test(keyId) : provider.allowsKeyId(keyId);
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

  private static int readUint16(byte[] bytes, int offset) {
    return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
  }
```

- [ ] **Step 4: Run** — `./mvnw -q -pl codec-crypto test` — Expected: PASS.

- [ ] **Step 5: Commit** — `git add codec-crypto && git commit -m "Add EnvelopeCodec decode with validation order and admission"`

---

### Task 9: Adversarial tests — tamper matrix, AAD matrix, cross-provider, composition

**Files:**
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/EnvelopeCodecAdversarialTest.java`

**Interfaces:**
- Consumes: the complete `EnvelopeCodec`, `JceDataKeyProvider`, `GzipCodec` (from codec-core), `DecryptionException`.
- Produces: nothing new — confidence.

- [ ] **Step 1: Write the tests (they should PASS immediately if Tasks 7–8 are correct; a failure here is a real bug — STOP and report, do not adjust assertions)**

```java
package org.jwcarman.codec.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.transform.GzipCodec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EnvelopeCodecAdversarialTest {

  private static JceDataKeyProvider provider() {
    byte[] kek = new byte[32];
    java.util.Arrays.fill(kek, (byte) 7);
    return new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES")));
  }

  @Nested
  class Tamper_matrix {
    @Test
    void every_single_byte_flip_in_the_message_is_rejected() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] message = codec.encode("tamper target".getBytes(UTF_8));
      for (int i = 0; i < message.length; i++) {
        byte[] mutated = message.clone();
        mutated[i] ^= 0x01;
        int index = i;
        assertThatExceptionOfType(RuntimeException.class)
            .as("flipping byte %d must be rejected", index)
            .isThrownBy(() -> codec.decode(mutated))
            .isInstanceOfAny(DecryptionException.class);
      }
    }

    @Test
    void a_truncated_tag_is_rejected() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] message = codec.encode("x".getBytes(UTF_8));
      byte[] truncated = java.util.Arrays.copyOf(message, message.length - 1);
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(truncated));
    }

    @Test
    void an_appended_trailing_byte_is_rejected() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] message = codec.encode("x".getBytes(UTF_8));
      byte[] extended = java.util.Arrays.copyOf(message, message.length + 1);
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> codec.decode(extended));
    }
  }

  @Nested
  class Aad_matrix {
    @Test
    void ciphertext_bound_to_one_aad_is_rejected_under_another() {
      byte[] message = EnvelopeCodec.builder(provider()).aad("tenant-1".getBytes(UTF_8))
          .build().encode("x".getBytes(UTF_8));
      EnvelopeCodec other =
          EnvelopeCodec.builder(provider()).aad("tenant-2".getBytes(UTF_8)).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> other.decode(message))
          .withMessage("Unable to decrypt data");
    }

    @Test
    void aad_bound_ciphertext_is_rejected_by_an_aad_less_codec() {
      byte[] message = EnvelopeCodec.builder(provider()).aad("tenant-1".getBytes(UTF_8))
          .build().encode("x".getBytes(UTF_8));
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(message));
    }

    @Test
    void plain_ciphertext_is_rejected_by_an_aad_bound_codec() {
      byte[] message = EnvelopeCodec.builder(provider()).build().encode("x".getBytes(UTF_8));
      EnvelopeCodec bound =
          EnvelopeCodec.builder(provider()).aad("tenant-1".getBytes(UTF_8)).build();
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> bound.decode(message));
    }
  }

  @Nested
  class Cross_provider {
    @Test
    void a_different_provider_instance_sharing_the_kek_decodes_the_message() {
      byte[] kek = new byte[32];
      java.util.Arrays.fill(kek, (byte) 7);
      SecretKey shared = new SecretKeySpec(kek, "AES");
      EnvelopeCodec writer = EnvelopeCodec.builder(
          new JceDataKeyProvider("kek", Map.of("kek", shared))).build();
      EnvelopeCodec reader = EnvelopeCodec.builder(
          new JceDataKeyProvider("kek", Map.of("kek", shared))).build();
      byte[] plaintext = "portable".getBytes(UTF_8);
      assertThat(reader.decode(writer.encode(plaintext))).isEqualTo(plaintext);
    }
  }

  @Nested
  class Composition {
    @Test
    void compress_then_encrypt_round_trips_through_and_then() {
      org.jwcarman.codec.spi.Codec<byte[]> chain =
          new GzipCodec().andThen(EnvelopeCodec.builder(provider()).build());
      byte[] plaintext = "the quick brown fox ".repeat(100).getBytes(UTF_8);
      assertThat(chain.decode(chain.encode(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void gzip_output_fed_directly_to_decode_is_rejected_fast_on_magic() {
      byte[] gzipped = new GzipCodec().encode("not encrypted".getBytes(UTF_8));
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> EnvelopeCodec.builder(provider()).build().decode(gzipped))
          .withMessageContaining("magic");
    }
  }
}
```

Note on the tamper matrix: byte flips in the two length fields can produce either a structural rejection or a cryptographic one depending on the resulting parse — both are `DecryptionException`; the test asserts the type, not the message, for exactly that reason.

- [ ] **Step 2: Run** — `./mvnw -q -pl codec-crypto test` — Expected: PASS. Any failure is a Task 7/8 bug: STOP, report NEEDS_CONTEXT or fix the implementation (never the assertions).

- [ ] **Step 3: Commit** — `git add codec-crypto && git commit -m "Add adversarial tests: tamper matrix, AAD matrix, composition"`

---

### Task 10: Frozen wire-format vector

**Files:**
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/WireFormatVectorTest.java`

**Interfaces:**
- Consumes: `EnvelopeCodec.Builder.secureRandom`, `JceDataKeyProvider(String, Map, SecureRandom)`.
- Produces: a committed byte-level conformance vector; any future change to the wire format fails this test.

- [ ] **Step 1: Write the vector test with a bootstrap**

```java
package org.jwcarman.codec.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WireFormatVectorTest {

  /** Deterministic byte source: 0, 1, 2, ... — DEK first (32 bytes), then nonce (12 bytes). */
  private static SecureRandom sequentialRandom() {
    return new SecureRandom() {
      private int next = 0;

      @Override
      public void nextBytes(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
          bytes[i] = (byte) next++;
        }
      }
    };
  }

  private static EnvelopeCodec deterministicCodec() {
    byte[] kek = new byte[32]; // all zeros, deliberately
    SecureRandom random = sequentialRandom();
    return EnvelopeCodec.builder(
            new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES")), random))
        .secureRandom(random)
        .build();
  }

  private static final String FROZEN_VECTOR_HEX = "BOOTSTRAP_ME";

  @Test
  void the_wire_format_matches_the_frozen_vector_byte_for_byte() {
    byte[] message = deterministicCodec().encode("codec-crypto v1".getBytes(UTF_8));
    assertThat(HexFormat.of().formatHex(message)).isEqualTo(FROZEN_VECTOR_HEX);
  }

  @Test
  void the_frozen_vector_still_decodes() {
    byte[] kek = new byte[32];
    EnvelopeCodec reader = EnvelopeCodec.builder(
            new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES"))))
        .build();
    byte[] frozen = HexFormat.of().parseHex(FROZEN_VECTOR_HEX);
    assertThat(reader.decode(frozen)).isEqualTo("codec-crypto v1".getBytes(UTF_8));
  }
}
```

- [ ] **Step 2: Bootstrap the vector**

Run the first test once: `./mvnw -q -pl codec-crypto test -Dtest=WireFormatVectorTest 2>&1 | head -30`. It fails; the assertion output prints the actual hex. Copy that exact hex string into `FROZEN_VECTOR_HEX`, replacing `BOOTSTRAP_ME`. Sanity-check it by eye before committing: it must start with `4a4301 01` (magic, version, algorithm — ignoring spacing) followed by `0003 6b656b` (keyId length 3, "kek").

- [ ] **Step 3: Run both tests** — `./mvnw -q -pl codec-crypto test -Dtest=WireFormatVectorTest` — Expected: PASS. The second test proves the vector decodes with a NON-deterministic reader, so the vector is real ciphertext, not an artifact of the seams.

- [ ] **Step 4: Full module suite** — `./mvnw -Pci -B clean verify` — Expected: BUILD SUCCESS, all 10 modules, no new gate allowlist entries.

- [ ] **Step 5: Commit** — `git add codec-crypto && git commit -m "Freeze the v1 wire-format vector"`

---

### Task 11: Documentation and PRD corrections

**Files:**
- Create: `docs/guides/encryption.md`
- Modify: `mkdocs.yml` (nav)
- Modify: `PRD.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: the shipped API names verbatim (grep them before writing — truth discipline per the docs-writer agent definition).

- [ ] **Step 1: Write `docs/guides/encryption.md`** covering, in this order: quickstart (JCE provider + builder through `andThen`); the envelope model (DEK/KEK, what travels in the message); key rotation via keyIds; the strategy choice with the KMS cost stated loudly and `BoundedDataKeyStrategy` bounds explained; implementing `DataKeyProvider` against a KMS (the security contract's two MUSTs restated, the GCP note); the wire format table copied byte-for-byte from the spec plus "proprietary and permanent" statement; admonitions for compress-then-encrypt ordering, CRIME/BREACH, ciphertext substitution (fixed AAD limitation), and no-secrets-in-keyIds; error taxonomy (never quarantine on `KeyAccessException`). End with a "Where next" style pair of links to the composition guide and getting-started. Code snippets must be name-checked against source.

- [ ] **Step 2: Add to nav** in `mkdocs.yml` under Guides, after Codec Composition: `      - Encryption: guides/encryption.md`

- [ ] **Step 3: Verify the site** — `python3 -m mkdocs build --strict` — Expected: exit 0.

- [ ] **Step 4: PRD corrections** in `PRD.md`: (a) the `Codec<T>` interface block drops `Class<T> type();` and gains `andThen`; (b) delete the `StringCodec`/`ByteArrayCodec` bullets and the "In-memory/passthrough fallback" goal line; (c) CodecFactory block shows `create(TypeRef)` + default `create(Class)`; (d) module table gains `codec-crypto`; (e) "Compression beyond built-in gzip" non-goal sentence updated: encryption is no longer consumer-supplied — `codec-crypto` ships it; (f) Future Considerations: remove the MessagePack bullet's "future module" framing (it is a Jackson mapper swap), leave Avro/Kryo.

- [ ] **Step 5: CHANGELOG** — under `## [Unreleased]`, add an `### Added` section:

```markdown
### Added
- `codec-crypto`: AES-256-GCM envelope-encryption `Codec<byte[]>` transform with
  pluggable key management (`DataKeyProvider` SPI — in-process JCE or remote
  KMS), fresh-DEK-per-message default with opt-in bounded caching, and a
  versioned self-describing wire format
```

- [ ] **Step 6: Full verify + commit** — `./mvnw -Pci -B clean verify && python3 -m mkdocs build --strict`, then `git add -A && git commit -m "Document codec-crypto and correct stale PRD claims"`

---

## Self-review record

- Spec coverage: every spec component (3 exceptions, DataKey, both SPIs, both strategies, JCE provider, EnvelopeCodec, wire format, decode order, admission, taxonomy, frozen vector, docs requirements, PRD/CHANGELOG DoD items) maps to a task. The spec's "Docs requirement" items land in Task 7 javadoc + Task 11 site docs.
- Placeholders: the single deliberate one is `BOOTSTRAP_ME` in Task 10, with an explicit bootstrap step and a byte-prefix sanity check.
- Type consistency: signatures in later tasks were checked against their defining tasks (`acquire(DataKeyProvider)`, `DecryptionException.cryptographic(Throwable)`, builder method names, wire constants).
