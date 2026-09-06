# codec-versioned Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship `codec-versioned` — a `Codec<T>` wrapper that prefixes each payload with a magic + version header and dispatches decoding on that version, so an application can change its storage strategy without a flag day, per spec 007.

**Architecture:** A new zero-external-dependency module depending only on `codec-core`. Two exception types carry decode failures; a `VersionedCodec.Builder<T>` collects `version → Codec<T>` registrations plus an explicit write version and validates them at build time; the built codec is an immutable `Codec<T>` holding a 256-entry dispatch array. Because it is generic over `Codec<T>`, versioned byte transforms are the `T = byte[]` case and need no extra code.

**Tech Stack:** Java 25, `codec-core` only, JUnit 5 + AssertJ (NO Mockito — not on the classpath).

**Spec:** `docs/superpowers/specs/007-codec-versioned.md` — normative. If this plan and the spec disagree, STOP and report; do not pick one silently.

## Global Constraints

- `codec-versioned` depends on `codec-core` only; zero external compile dependencies. The ci profile's `dependency:analyze-only` (failOnWarning) and enforcer gates enforce this — never edit their allowlists.
- Verification command for every task: `./mvnw -Pci -B clean verify` (plain `verify` skips the gates and is NOT sufficient). To run one module's tests quickly while iterating: `./mvnw -B -pl codec-versioned -am test`.
- No `@SuppressWarnings`, and no suppression of any kind. No star imports (regular or static). Apache 2.0 license header on every new `.java` and `pom.xml` — copy the exact block from a neighbor file (`codec-transforms/pom.xml`, `codec-core/src/main/java/org/jwcarman/codec/spi/Codec.java`).
- Tests: `@Nested` classes named as capitalized phrases with underscores, `snake_case` sentence method names, `@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)` on the outer class. House style reference: `codec-transforms/src/test/java/org/jwcarman/codec/transform/encoding/HexCodecTest.java`.
- Format before committing: `./mvnw -q spotless:apply`. Apply license headers on new files with `./mvnw -q -Plicense license:format`.
- Commit trailers on every commit:
  ```
  Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01PELEtnHV8gBVNcGyfXuvrq
  ```
  Never push.
- Package for all main and test code: `org.jwcarman.codec.versioned`.
- Wire constants (spec-normative, used across tasks): magic bytes `0xC0`, `0xDC` in that order; header length 3; version is the third byte read as unsigned (`bytes[2] & 0xFF`); valid version range `1..255`; `0` is reserved and never registrable.
- Javadoc is required on every public type, constructor, method, and parameter — the build's doclint runs in the release profile and missing tags will fail it later. Follow the density of `codec-core/src/main/java/org/jwcarman/codec/spi/Codec.java`.

---

### Task 1: Module scaffold

**Files:**
- Create: `codec-versioned/pom.xml`
- Modify: `pom.xml` (parent `<modules>` list, after `<module>codec-transforms</module>`)
- Modify: `codec-bom/pom.xml` (managed dependency entry, after the `codec-transforms` entry)

**Interfaces:**
- Consumes: nothing.
- Produces: a building module every later task lands in; the BOM manages `org.jwcarman.codec:codec-versioned`.

- [ ] **Step 1: Create the module pom**

Create `codec-versioned/pom.xml`. Copy the XML comment license header verbatim from the top of `codec-transforms/pom.xml`, then this body:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.jwcarman.codec</groupId>
        <artifactId>codec-parent</artifactId>
        <version>0.8.0-SNAPSHOT</version>
    </parent>

    <artifactId>codec-versioned</artifactId>
    <name>Codec Versioned</name>
    <description>Format versioning for Codec: a magic + version header that lets a storage strategy change without a flag day</description>

    <properties>
        <module.name>org.jwcarman.codec.versioned</module.name>
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
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-params</artifactId>
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

Confirm the `<version>` in `<parent>` matches the current value in the root `pom.xml` — if the project has moved past `0.8.0-SNAPSHOT`, use whatever is there.

- [ ] **Step 2: Register the module in the parent POM**

In the root `pom.xml`, inside `<modules>`, add the new module directly after `codec-transforms`:

```xml
        <module>codec-transforms</module>
        <module>codec-versioned</module>
```

- [ ] **Step 3: Register the artifact in the BOM**

In `codec-bom/pom.xml`, inside `<dependencyManagement><dependencies>`, add an entry directly after the `codec-transforms` one, matching the surrounding formatting exactly:

```xml
            <dependency>
                <groupId>org.jwcarman.codec</groupId>
                <artifactId>codec-versioned</artifactId>
                <version>${project.version}</version>
            </dependency>
```

- [ ] **Step 4: Verify the build picks it up**

Run: `./mvnw -Pci -B clean verify`
Expected: BUILD SUCCESS, and `Codec Versioned` appears in the reactor summary. The module has no sources yet, which is fine.

- [ ] **Step 5: Commit**

```bash
git add codec-versioned/pom.xml pom.xml codec-bom/pom.xml
git commit -m "Add the codec-versioned module scaffold"
```

---

### Task 2: Exception types

**Files:**
- Create: `codec-versioned/src/main/java/org/jwcarman/codec/versioned/VersionedFormatException.java`
- Create: `codec-versioned/src/main/java/org/jwcarman/codec/versioned/UnknownVersionException.java`
- Test: `codec-versioned/src/test/java/org/jwcarman/codec/versioned/UnknownVersionExceptionTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `public class VersionedFormatException extends RuntimeException` with `public VersionedFormatException(String message)`.
  - `public class UnknownVersionException extends VersionedFormatException` with `public UnknownVersionException(int version)` and `public int version()`.

  Task 3 throws both from `decode`.

- [ ] **Step 1: Write the failing test**

Create `codec-versioned/src/test/java/org/jwcarman/codec/versioned/UnknownVersionExceptionTest.java` (license header first, copied from `codec-transforms/src/test/java/org/jwcarman/codec/transform/encoding/HexCodecTest.java`):

```java
package org.jwcarman.codec.versioned;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UnknownVersionExceptionTest {

  @Nested
  class An_unknown_version {

    @Test
    void reports_the_offending_version() {
      UnknownVersionException exception = new UnknownVersionException(7);

      assertThat(exception.version()).isEqualTo(7);
    }

    @Test
    void names_the_offending_version_in_its_message() {
      UnknownVersionException exception = new UnknownVersionException(7);

      assertThat(exception).hasMessage("unknown format version: 7");
    }

    @Test
    void is_a_versioned_format_exception() {
      assertThat(new UnknownVersionException(7)).isInstanceOf(VersionedFormatException.class);
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -B -pl codec-versioned -am test`
Expected: COMPILATION FAILURE — `UnknownVersionException` and `VersionedFormatException` do not exist.

- [ ] **Step 3: Write the implementation**

`VersionedFormatException.java` (license header first):

```java
package org.jwcarman.codec.versioned;

/**
 * Signals that a buffer handed to a versioned codec is not a well-formed versioned payload: it is
 * shorter than the three-byte header, or it does not carry the versioned magic.
 *
 * <p>This is the "these bytes were never ours" failure — a codec pointed at data some other codec
 * wrote. Contrast {@link UnknownVersionException}, which means the framing is ours but the version
 * is not one this codec knows.
 */
public class VersionedFormatException extends RuntimeException {

  /**
   * Creates an exception with the given message.
   *
   * @param message the detail message
   */
  public VersionedFormatException(String message) {
    super(message);
  }
}
```

`UnknownVersionException.java` (license header first):

```java
package org.jwcarman.codec.versioned;

/**
 * Signals that a payload carries valid versioned framing but names a version this codec has no
 * codec registered for.
 *
 * <p>During a rollout this is the "written by a newer deploy" signal — a condition a caller may
 * choose to route or retry rather than treat as corruption — which is why it is distinguishable
 * from its {@link VersionedFormatException} parent.
 */
public class UnknownVersionException extends VersionedFormatException {

  private final int version;

  /**
   * Creates an exception naming the version that could not be dispatched.
   *
   * @param version the unrecognized format version, 0-255
   */
  public UnknownVersionException(int version) {
    super("unknown format version: " + version);
    this.version = version;
  }

  /**
   * Returns the unrecognized format version read from the payload.
   *
   * @return the version, 0-255
   */
  public int version() {
    return version;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -B -pl codec-versioned -am test`
Expected: PASS, 3 tests.

- [ ] **Step 5: Format, apply headers, and commit**

```bash
./mvnw -q spotless:apply
./mvnw -q -Plicense license:format
git add codec-versioned/src
git commit -m "codec-versioned: decode failure exception types"
```

---

### Task 3: VersionedCodec and its builder

**Files:**
- Create: `codec-versioned/src/main/java/org/jwcarman/codec/versioned/VersionedCodec.java`
- Test: `codec-versioned/src/test/java/org/jwcarman/codec/versioned/VersionedCodecTest.java`

**Interfaces:**
- Consumes: `VersionedFormatException(String)` and `UnknownVersionException(int)` from Task 2; `org.jwcarman.codec.spi.Codec<T>` from `codec-core`.
- Produces:
  - `public final class VersionedCodec` — not instantiable; holds `public static <T> Builder<T> builder()`.
  - `public static final class VersionedCodec.Builder<T>` with `Builder<T> version(int version, Codec<T> codec)`, `Builder<T> writing(int version)`, and `Codec<T> build()`.

  Task 4 documents this API; no later task calls it in main code.

This whole task is one deliverable — the codec is meaningless without its builder and the builder has nothing to build without the codec — so both land together, but the test cycle below is split so each behavior is driven by a failing test.

- [ ] **Step 1: Write the failing round-trip and framing tests**

Create `codec-versioned/src/test/java/org/jwcarman/codec/versioned/VersionedCodecTest.java` (license header first):

```java
package org.jwcarman.codec.versioned;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Locale;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jwcarman.codec.spi.Codec;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class VersionedCodecTest {

  private static final byte MAGIC_0 = (byte) 0xC0;
  private static final byte MAGIC_1 = (byte) 0xDC;

  /** Encodes a string as UTF-8, upper-cased on the way out so v1 and v2 payloads differ. */
  private static Codec<String> upperCase() {
    return new Codec<>() {
      @Override
      public byte[] encode(String value) {
        return value.toUpperCase(Locale.ROOT).getBytes(UTF_8);
      }

      @Override
      public String decode(byte[] bytes) {
        return new String(bytes, UTF_8).toLowerCase(Locale.ROOT);
      }
    };
  }

  private static Codec<String> plain() {
    return new Codec<>() {
      @Override
      public byte[] encode(String value) {
        return value.getBytes(UTF_8);
      }

      @Override
      public String decode(byte[] bytes) {
        return new String(bytes, UTF_8);
      }
    };
  }

  @Nested
  class A_versioned_codec {

    @Test
    void round_trips_through_the_write_version() {
      Codec<String> codec =
          VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

      assertThat(codec.decode(codec.encode("hello"))).isEqualTo("hello");
    }

    @Test
    void prefixes_the_payload_with_magic_and_the_write_version() {
      Codec<String> codec =
          VersionedCodec.<String>builder()
              .version(1, plain())
              .version(2, plain())
              .writing(2)
              .build();

      byte[] encoded = codec.encode("hi");

      assertThat(encoded).containsExactly(MAGIC_0, MAGIC_1, 2, 'h', 'i');
    }

    @Test
    void reads_a_payload_written_at_an_older_version() {
      Codec<String> writer =
          VersionedCodec.<String>builder().version(1, plain()).writing(1).build();
      Codec<String> reader =
          VersionedCodec.<String>builder()
              .version(1, plain())
              .version(2, upperCase())
              .writing(2)
              .build();

      assertThat(reader.decode(writer.encode("hello"))).isEqualTo("hello");
    }

    @Test
    void dispatches_each_version_to_its_own_codec() {
      Codec<String> codec =
          VersionedCodec.<String>builder()
              .version(1, plain())
              .version(2, upperCase())
              .writing(2)
              .build();

      byte[] encoded = codec.encode("hello");

      assertThat(encoded).containsExactly(MAGIC_0, MAGIC_1, 2, 'H', 'E', 'L', 'L', 'O');
      assertThat(codec.decode(encoded)).isEqualTo("hello");
    }

    @Test
    void hands_an_empty_payload_to_the_delegate() {
      Codec<String> codec =
          VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

      assertThat(codec.decode(new byte[] {MAGIC_0, MAGIC_1, 1})).isEmpty();
    }

    @Test
    void rejects_a_null_value_on_encode() {
      Codec<String> codec =
          VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

      assertThatNullPointerException().isThrownBy(() -> codec.encode(null));
    }

    @Test
    void rejects_a_null_buffer_on_decode() {
      Codec<String> codec =
          VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

      assertThatNullPointerException().isThrownBy(() -> codec.decode(null));
    }

    @Test
    void passes_null_through_when_wrapped_null_safe() {
      Codec<String> codec =
          VersionedCodec.<String>builder().version(1, plain()).writing(1).build().nullSafe();

      assertThat(codec.encode(null)).isNull();
      assertThat(codec.decode(null)).isNull();
    }

    @Test
    void propagates_a_delegate_failure_unchanged() {
      Codec<String> exploding =
          new Codec<>() {
            @Override
            public byte[] encode(String value) {
              throw new IllegalStateException("boom");
            }

            @Override
            public String decode(byte[] bytes) {
              throw new IllegalStateException("boom");
            }
          };
      Codec<String> codec =
          VersionedCodec.<String>builder().version(1, exploding).writing(1).build();

      assertThatIllegalStateException().isThrownBy(() -> codec.encode("hi")).withMessage("boom");
      assertThatIllegalStateException()
          .isThrownBy(() -> codec.decode(new byte[] {MAGIC_0, MAGIC_1, 1}))
          .withMessage("boom");
    }
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw -B -pl codec-versioned -am test`
Expected: COMPILATION FAILURE — `VersionedCodec` does not exist.

- [ ] **Step 3: Write the implementation**

Create `codec-versioned/src/main/java/org/jwcarman/codec/versioned/VersionedCodec.java` (license header first). This is the whole class, including the validation Step 5 will test:

```java
package org.jwcarman.codec.versioned;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jwcarman.codec.spi.Codec;

/**
 * Prefixes every payload with a small self-describing header naming the codec that produced it, and
 * dispatches on that header when decoding — so an application can change its storage strategy
 * without rewriting what it has already stored.
 *
 * <p>The frame is three bytes: two magic bytes ({@code 0xC0 0xDC}), then an unsigned version in
 * {@code 1..255}, then the bytes the codec registered at that version produced.
 *
 * {@snippet lang = java :
 * Codec<Person> codec = VersionedCodec.<Person>builder()
 *         .version(1, jacksonFactory.create(Person.class))
 *         .version(2, foryFactory.create(Person.class).andThen(new ZstdCodec()))
 *         .writing(2)
 *         .build();
 * }
 *
 * <p>The write version is named explicitly rather than inferred from the highest registration,
 * because that is what makes a two-phase rollout possible: deploy with the new version registered
 * but still {@code .writing(1)}, let that reach every instance, then flip to {@code .writing(2)} in
 * a second deploy. No instance is ever handed data it cannot read.
 *
 * <p><strong>Ordering matters.</strong> Anything layered <em>outside</em> a versioned codec must be
 * undone before the header can be read, so it is frozen for the life of the store:
 *
 * {@snippet lang = java :
 * // header outermost: backend, transforms, everything inside may differ per version
 * VersionedCodec.<Person>builder()
 *         .version(1, jackson.create(Person.class).andThen(new GzipCodec()))
 *         .version(2, fory.create(Person.class).andThen(new ZstdCodec()))
 *         .writing(2)
 *         .build();
 *
 * // header inside gzip: gzip can now never change
 * versioned.andThen(new GzipCodec());
 * }
 *
 * <p>Put whatever you might want to change inside the versioned codec; put only permanent
 * commitments outside it.
 *
 * <p>Because the builder is generic over {@link Codec}, versioned byte transforms are simply the
 * {@code T = byte[]} case — a compression or key-wrapping layer that can be swapped under an
 * unchanged value codec.
 *
 * <p>Built codecs are immutable and thread-safe whenever their delegates are, which the {@link
 * Codec} contract already requires. Builders are not thread-safe.
 */
public final class VersionedCodec {

  private static final byte MAGIC_0 = (byte) 0xC0;
  private static final byte MAGIC_1 = (byte) 0xDC;
  private static final int HEADER_LENGTH = 3;
  private static final int MIN_VERSION = 1;
  private static final int MAX_VERSION = 255;

  private VersionedCodec() {}

  /**
   * Starts building a versioned codec.
   *
   * @param <T> the type the codec converts
   * @return a new builder
   */
  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * Collects the per-version codecs and the version to write, and validates them.
   *
   * @param <T> the type the codec converts
   */
  public static final class Builder<T> {

    private final Map<Integer, Codec<T>> registrations = new HashMap<>();
    private int writeVersion = -1;

    private Builder() {}

    /**
     * Registers the codec that reads (and, if named by {@link #writing}, writes) a version.
     *
     * @param version the format version, 1-255; 0 is reserved
     * @param codec the codec that produced and can read that version's payloads
     * @return this builder
     * @throws IllegalArgumentException if {@code version} is outside 1-255
     * @throws IllegalStateException if {@code version} is already registered
     * @throws NullPointerException if {@code codec} is null
     */
    public Builder<T> version(int version, Codec<T> codec) {
      if (version < MIN_VERSION || version > MAX_VERSION) {
        throw new IllegalArgumentException(
            "version must be between " + MIN_VERSION + " and " + MAX_VERSION + ": " + version);
      }
      Objects.requireNonNull(codec, "codec must not be null");
      if (registrations.containsKey(version)) {
        throw new IllegalStateException("version already registered: " + version);
      }
      registrations.put(version, codec);
      return this;
    }

    /**
     * Names the version {@link Codec#encode} writes. Required.
     *
     * @param version the format version to write, 1-255
     * @return this builder
     * @throws IllegalArgumentException if {@code version} is outside 1-255
     */
    public Builder<T> writing(int version) {
      if (version < MIN_VERSION || version > MAX_VERSION) {
        throw new IllegalArgumentException(
            "version must be between " + MIN_VERSION + " and " + MAX_VERSION + ": " + version);
      }
      writeVersion = version;
      return this;
    }

    /**
     * Builds the codec.
     *
     * @return an immutable versioned codec
     * @throws IllegalStateException if no version was registered, if {@link #writing} was never
     *     called, or if it names a version with no registered codec
     */
    public Codec<T> build() {
      if (registrations.isEmpty()) {
        throw new IllegalStateException("at least one version must be registered");
      }
      if (writeVersion < 0) {
        throw new IllegalStateException("a write version must be set with writing(int)");
      }
      if (!registrations.containsKey(writeVersion)) {
        throw new IllegalStateException("no codec registered for write version: " + writeVersion);
      }
      return new Dispatcher<>(Map.copyOf(registrations), writeVersion);
    }
  }

  private record Dispatcher<T>(Map<Integer, Codec<T>> codecs, int writeVersion)
      implements Codec<T> {

    @Override
    public byte[] encode(T value) {
      Objects.requireNonNull(value, "value must not be null");
      byte[] payload = codecs.get(writeVersion).encode(value);
      byte[] framed = new byte[HEADER_LENGTH + payload.length];
      framed[0] = MAGIC_0;
      framed[1] = MAGIC_1;
      framed[2] = (byte) writeVersion;
      System.arraycopy(payload, 0, framed, HEADER_LENGTH, payload.length);
      return framed;
    }

    @Override
    public T decode(byte[] bytes) {
      Objects.requireNonNull(bytes, "bytes must not be null");
      if (bytes.length < HEADER_LENGTH) {
        throw new VersionedFormatException(
            "not a versioned payload: expected at least "
                + HEADER_LENGTH
                + " bytes, got "
                + bytes.length);
      }
      if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) {
        throw new VersionedFormatException("not a versioned payload: bad magic");
      }
      int version = bytes[2] & 0xFF;
      Codec<T> codec = codecs.get(version);
      if (codec == null) {
        throw new UnknownVersionException(version);
      }
      return codec.decode(Arrays.copyOfRange(bytes, HEADER_LENGTH, bytes.length));
    }
  }
}
```

Two things worth knowing about this implementation:

- **Dispatch is a `Map<Integer, Codec<T>>`, not the array spec 007 sketched.** A
  generic array (`new Codec[256]`) cannot be created without an unchecked cast, and
  the project forbids suppressions of every kind. An immutable map lookup is the
  honest cost of that rule, and it is not measurable against the delegate
  serialization it guards. Record the trade in the commit message.
- **`build()` checks emptiness before the write version**, so a builder with no
  registrations at all reports that, rather than complaining about the write
  version. A test in Step 5 pins this ordering.

- [ ] **Step 4: Run to verify the round-trip tests pass**

Run: `./mvnw -B -pl codec-versioned -am test`
Expected: PASS.

- [ ] **Step 5: Write the failing framing-error and builder-validation tests**

Append these nested classes inside `VersionedCodecTest` (before its closing brace):

```java
  @Nested
  class A_buffer_that_is_not_a_versioned_payload {

    private final Codec<String> codec =
        VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

    @Test
    void is_rejected_when_empty() {
      assertThatExceptionOfType(VersionedFormatException.class)
          .isThrownBy(() -> codec.decode(new byte[0]));
    }

    @ParameterizedTest(name = "{0} byte(s)")
    @ValueSource(ints = {1, 2})
    void is_rejected_when_shorter_than_the_header(int length) {
      byte[] truncated = new byte[length];
      truncated[0] = MAGIC_0;

      assertThatExceptionOfType(VersionedFormatException.class)
          .isThrownBy(() -> codec.decode(truncated));
    }

    @Test
    void is_rejected_when_the_magic_does_not_match() {
      assertThatExceptionOfType(VersionedFormatException.class)
          .isThrownBy(() -> codec.decode(new byte[] {'{', '"', 'a', '"', '}'}));
    }

    @Test
    void is_not_reported_as_an_unknown_version() {
      assertThatExceptionOfType(VersionedFormatException.class)
          .isThrownBy(() -> codec.decode(new byte[] {0x00, 0x00, 0x01}))
          .isNotInstanceOf(UnknownVersionException.class);
    }
  }

  @Nested
  class A_version_with_no_registered_codec {

    private final Codec<String> codec =
        VersionedCodec.<String>builder().version(1, plain()).writing(1).build();

    @Test
    void is_rejected_naming_the_version() {
      assertThatExceptionOfType(UnknownVersionException.class)
          .isThrownBy(() -> codec.decode(new byte[] {MAGIC_0, MAGIC_1, (byte) 9}))
          .satisfies(e -> assertThat(e.version()).isEqualTo(9));
    }

    @Test
    void is_rejected_for_the_reserved_zero_version() {
      assertThatExceptionOfType(UnknownVersionException.class)
          .isThrownBy(() -> codec.decode(new byte[] {MAGIC_0, MAGIC_1, 0x00}))
          .satisfies(e -> assertThat(e.version()).isZero());
    }

    @Test
    void reads_the_version_byte_as_unsigned() {
      Codec<String> wide =
          VersionedCodec.<String>builder().version(255, plain()).writing(255).build();

      byte[] encoded = wide.encode("hi");

      assertThat(encoded[2]).isEqualTo((byte) 0xFF);
      assertThat(wide.decode(encoded)).isEqualTo("hi");
    }
  }

  @Nested
  class The_builder {

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = {-1, 0, 256})
    void rejects_a_version_outside_the_valid_range(int version) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> VersionedCodec.<String>builder().version(version, plain()));
    }

    @ParameterizedTest(name = "version {0}")
    @ValueSource(ints = {-1, 0, 256})
    void rejects_a_write_version_outside_the_valid_range(int version) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> VersionedCodec.<String>builder().writing(version));
    }

    @Test
    void rejects_a_null_codec() {
      assertThatNullPointerException()
          .isThrownBy(() -> VersionedCodec.<String>builder().version(1, null));
    }

    @Test
    void rejects_registering_the_same_version_twice() {
      assertThatIllegalStateException()
          .isThrownBy(
              () -> VersionedCodec.<String>builder().version(1, plain()).version(1, upperCase()));
    }

    @Test
    void rejects_building_with_no_versions_registered() {
      assertThatIllegalStateException()
          .isThrownBy(() -> VersionedCodec.<String>builder().writing(1).build());
    }

    @Test
    void rejects_building_without_a_write_version() {
      assertThatIllegalStateException()
          .isThrownBy(() -> VersionedCodec.<String>builder().version(1, plain()).build());
    }

    @Test
    void rejects_a_write_version_with_no_registered_codec() {
      assertThatIllegalStateException()
          .isThrownBy(() -> VersionedCodec.<String>builder().version(1, plain()).writing(2).build());
    }

    @Test
    void does_not_leak_later_registrations_into_a_built_codec() {
      VersionedCodec.Builder<String> builder =
          VersionedCodec.<String>builder().version(1, plain()).writing(1);
      Codec<String> built = builder.build();
      builder.version(2, upperCase());

      assertThatExceptionOfType(UnknownVersionException.class)
          .isThrownBy(() -> built.decode(new byte[] {MAGIC_0, MAGIC_1, 2}));
    }
  }
```

- [ ] **Step 6: Run and confirm they pass**

Run: `./mvnw -B -pl codec-versioned -am test`
Expected: PASS. If `rejects_building_with_no_versions_registered` fails because the write-version check runs first, reorder `build()`'s checks so the emptiness check comes first, as written in Step 3.

- [ ] **Step 7: Add the versioned-transform test**

Append this nested class inside `VersionedCodecTest`:

```java
  @Nested
  class A_versioned_byte_transform {

    private static Codec<byte[]> reversing() {
      return new Codec<>() {
        @Override
        public byte[] encode(byte[] value) {
          byte[] out = new byte[value.length];
          for (int i = 0; i < value.length; i++) {
            out[i] = value[value.length - 1 - i];
          }
          return out;
        }

        @Override
        public byte[] decode(byte[] bytes) {
          return encode(bytes);
        }
      };
    }

    private static Codec<byte[]> identity() {
      return new Codec<>() {
        @Override
        public byte[] encode(byte[] value) {
          return value.clone();
        }

        @Override
        public byte[] decode(byte[] bytes) {
          return bytes.clone();
        }
      };
    }

    @Test
    void round_trips_inside_a_larger_chain() {
      Codec<byte[]> transform =
          VersionedCodec.<byte[]>builder()
              .version(1, identity())
              .version(2, reversing())
              .writing(2)
              .build();
      Codec<String> codec = plain().andThen(transform);

      assertThat(codec.decode(codec.encode("hello"))).isEqualTo("hello");
    }

    @Test
    void reads_a_payload_written_by_the_older_transform() {
      Codec<String> writer =
          plain().andThen(VersionedCodec.<byte[]>builder().version(1, identity()).writing(1).build());
      Codec<String> reader =
          plain()
              .andThen(
                  VersionedCodec.<byte[]>builder()
                      .version(1, identity())
                      .version(2, reversing())
                      .writing(2)
                      .build());

      assertThat(reader.decode(writer.encode("hello"))).isEqualTo("hello");
    }
  }
```

- [ ] **Step 8: Run and confirm**

Run: `./mvnw -B -pl codec-versioned -am test`
Expected: PASS.

- [ ] **Step 9: Add the package-info**

Create `codec-versioned/src/main/java/org/jwcarman/codec/versioned/package-info.java` (license header first), modeled on `codec-transforms/src/main/java/org/jwcarman/codec/transform/compress/package-info.java`:

```java
/**
 * Format versioning for codecs: a magic-and-version header that lets an application change its
 * storage strategy without rewriting what it has already stored.
 *
 * <p>{@link org.jwcarman.codec.versioned.VersionedCodec} is the entry point.
 */
package org.jwcarman.codec.versioned;
```

- [ ] **Step 10: Full verification**

Run: `./mvnw -Pci -B clean verify`
Expected: BUILD SUCCESS. If the coverage gate fails, add tests for the uncovered branch — do not lower the gate.

- [ ] **Step 11: Format, apply headers, and commit**

```bash
./mvnw -q spotless:apply
./mvnw -q -Plicense license:format
git add codec-versioned/src
git commit -m "codec-versioned: VersionedCodec and its builder

Dispatch is an immutable Map<Integer, Codec<T>> rather than the array the
spec sketched: a generic array needs an unchecked cast, and the project
forbids suppressions. The lookup is not measurable against the delegate
serialization it guards."
```

---

### Task 4: Documentation

**Files:**
- Modify: `README.md` (module table, and the composition section)
- Modify: `docs/guides/composition.md` (new section)
- Modify: `CHANGELOG.md` (`## [Unreleased]` → `### Added`)

**Interfaces:**
- Consumes: the `VersionedCodec.builder()` API from Task 3 — every snippet below must compile against it.
- Produces: nothing code depends on.

- [ ] **Step 1: Add the README module-table row**

In `README.md`, in the `## Modules` table, add a row directly after the Transforms row:

```markdown
| Versioned | Format versioning: a version header that lets the storage strategy change | `codec-versioned` |
```

- [ ] **Step 2: Add a README paragraph under the composition section**

In `README.md`, after the "Bring your own transform by implementing `Codec<byte[]>`" example block in `### 3. Compose codecs`, add:

```markdown
To change your storage strategy later without rewriting stored data, wrap the
codec in a versioned one (`codec-versioned`). It writes a 3-byte header naming
the version that produced each payload and dispatches on it when reading, so old
data stays readable while new data is written by the new strategy:

```java
Codec<Person> codec = VersionedCodec.<Person>builder()
        .version(1, jacksonFactory.create(Person.class))
        .version(2, foryFactory.create(Person.class).andThen(new ZstdCodec()))
        .writing(2)   // deploy readers first, then flip this
        .build();
```
```

(Take care with the nested fences — the inner block is a fenced `java` block inside the Markdown you are adding, not a nested fence in the file.)

- [ ] **Step 3: Add the composition guide section**

In `docs/guides/composition.md`, add a new `## Versioning the format` section immediately before the existing `## Custom transforms` section. Content:

```markdown
## Versioning the format

Bare codec output is not self-describing: bytes written by Jackson look exactly
like bytes written by Fory. Change backends — or add compression, or change it —
and everything already stored becomes unreadable the moment the new code
deploys.

`codec-versioned` fixes that by writing a three-byte header ahead of the
payload, `0xC0 0xDC` followed by an unsigned version, and dispatching decoding
on it:

```java
Codec<Person> codec = VersionedCodec.<Person>builder()
        .version(1, jacksonFactory.create(Person.class))
        .version(2, foryFactory.create(Person.class).andThen(new ZstdCodec()))
        .writing(2)
        .build();
```

Keep old versions registered and old data stays readable; nothing needs
rewriting.

The write version is explicit rather than "newest wins", and that is the point.
Deploy with version 2 registered but still `.writing(1)`, let it reach every
instance, then flip to `.writing(2)` in a second deploy. No instance is ever
handed data it cannot read. Data written by a version the reader does not know
raises `UnknownVersionException`, which carries the offending version — during a
rollout that means "written by a newer deploy", a condition you may want to
route or retry rather than treat as corruption. A buffer that is not versioned
at all raises the parent `VersionedFormatException`.

Because the builder is generic over `Codec<T>`, versioning a *transform* is the
`T = byte[]` case:

```java
Codec<byte[]> compression = VersionedCodec.<byte[]>builder()
        .version(1, new GzipCodec())
        .version(2, new ZstdCodec())
        .writing(2)
        .build();

Codec<Person> codec = factory.create(Person.class).andThen(compression);
```

### Where to put it in the chain

Anything layered *outside* a versioned codec has to be undone before the header
can be read, so it is frozen for the life of the store.

```java
// header outermost — backend, transforms, everything inside may differ per version
VersionedCodec.<Person>builder()
        .version(1, jackson.create(Person.class).andThen(new GzipCodec()))
        .version(2, fory.create(Person.class).andThen(new ZstdCodec()))
        .writing(2)
        .build();

// header inside gzip — gzip can now never change
versioned.andThen(new GzipCodec());
```

The second form is legitimate when the outer layer carries its own versioning —
`EnvelopeCodec` does, with its own magic and version byte — but make it a
deliberate choice. The rule: **put whatever you might want to change inside the
versioned codec, and only permanent commitments outside it.**
```

- [ ] **Step 4: Add the CHANGELOG entry**

In `CHANGELOG.md`, under `## [Unreleased]` → `### Added`, add a bullet in the style of its neighbors:

```markdown
- `codec-versioned`: `VersionedCodec` prefixes each payload with a magic and
  version header and dispatches decoding on it, so a storage strategy can change
  without a flag day — old versions stay registered and readable while an
  explicit write version makes a two-phase rollout possible
```

- [ ] **Step 5: Verify the docs build and the snippets are honest**

Run: `./mvnw -Pci -B clean verify`
Expected: BUILD SUCCESS.

Then re-read each snippet you added against `VersionedCodec` as committed in Task 3: every method name, argument order, and generic witness must match. A snippet that does not compile is a defect.

- [ ] **Step 6: Commit**

```bash
git add README.md docs/guides/composition.md CHANGELOG.md
git commit -m "Document codec-versioned"
```

---

## Notes for the executor

- `mkdocs.yml`'s `nav` needs no change: the versioning content lives inside the existing Composition guide, not a new page.
- No Spring Boot auto-configuration is in scope. Which versions exist and which one writes is application knowledge with nothing to detect from the classpath — spec 007, "Out of scope". Do not add a `codec-autoconfigure` entry.
- No legacy/unframed fallback is in scope, deliberately. If you find yourself wanting one to make a test pass, the test is wrong.
