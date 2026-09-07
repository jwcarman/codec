# Exception Families Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `Codec.encode` and `Codec.decode` one failure contract — four unchecked families under a `CodecException` root, keyed to what the caller does next — and bring every module's throw sites, tests, and docs onto it, per spec 008.

**Architecture:** Five classes land in `codec-core`'s `org.jwcarman.codec.spi` (Task 1). Every other module then rewrites its `encode`/`decode` throw sites to the family the spec's mapping table names, wrapping the underlying library exception as the cause, and rewrites the tests that asserted the old types (Tasks 2–7). `codec-crypto`'s and `codec-versioned`'s existing exception classes keep their names and change superclass. Docs, specs and CHANGELOG close it out (Task 8).

**Tech Stack:** Java 25, JUnit 5 + AssertJ (NO Mockito — not on the classpath). Maven reactor; `./mvnw -Pci -B clean verify` is the gate (enforcer, dependency analysis, SpotBugs+findsecbugs and PIT on `codec-crypto`, license headers, spotless).

**Spec:** `docs/superpowers/specs/008-codec-exceptions.md` — normative. If this plan and the spec disagree, STOP and report; do not pick one silently. The mapping table in the spec is the authority for which family each throw site lands in.

## Global Constraints

- Verification command for every task: `./mvnw -Pci -B clean verify` (plain `verify` skips the gates and is NOT sufficient). While iterating on one module: `./mvnw -B -pl <module> -am test`.
- No `@SuppressWarnings`, no suppression of any kind. No star imports, regular or static. Apache 2.0 license header on every new `.java` file — copy the block verbatim from `codec-core/src/main/java/org/jwcarman/codec/spi/Codec.java`.
- Javadoc on every public type, constructor, method, `@param`, `@return` and `@throws` — doclint runs in the release profile.
- Test house style: `@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)`, `@Nested` classes as capitalized underscore phrases, snake_case sentence method names — **except** in files that already use camelCase `should*` names (`JacksonCodecFactoryTest`, `GsonCodecFactoryTest`, `ProtobufCodecFactoryTest`, `TypeRefTest`): match the file you are in.
- Format before committing: `./mvnw -q spotless:apply`; apply headers with `./mvnw -q -Plicense license:format`.
- Commit trailers on every commit:
  ```
  Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01PELEtnHV8gBVNcGyfXuvrq
  ```
  Never push.
- Each module catches its **library's root exception type only** — never `RuntimeException` — so a genuine bug in the codec still surfaces as itself. The one documented deviation is `ForyCodec.decode` (Task 4), where malformed input has been verified to surface as `ForyException`, `IllegalArgumentException` *or* `IndexOutOfBoundsException`.
- **Messages do not change.** Every existing message string stays exactly as it is; only the exception type changes. New wrapper messages are given verbatim in the task that introduces them.
- Construction-time exceptions (`IllegalArgumentException`, `IllegalStateException`, `NullPointerException` from builders, constructors, `CodecFactory.create`, `encode(null)`, `decode(null)`) do **not** change. If a test asserts one of those, leave it alone.
- Exact family names, all in `org.jwcarman.codec.spi`: `CodecException` (root, protected constructors), `InvalidValueException`, `InvalidPayloadException`, `UnsupportedFormatException`, `TransientCodecException`.

---

### Task 1: The five classes in `codec-core`, and the `Codec` contract

**Files:**
- Create: `codec-core/src/main/java/org/jwcarman/codec/spi/CodecException.java`
- Create: `codec-core/src/main/java/org/jwcarman/codec/spi/InvalidValueException.java`
- Create: `codec-core/src/main/java/org/jwcarman/codec/spi/InvalidPayloadException.java`
- Create: `codec-core/src/main/java/org/jwcarman/codec/spi/UnsupportedFormatException.java`
- Create: `codec-core/src/main/java/org/jwcarman/codec/spi/TransientCodecException.java`
- Modify: `codec-core/src/main/java/org/jwcarman/codec/spi/Codec.java` (interface Javadoc and the `encode`/`decode` Javadoc, lines 21–45)
- Test: `codec-core/src/test/java/org/jwcarman/codec/spi/CodecExceptionsTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces, for every later task:
  - `public class CodecException extends RuntimeException` — `protected CodecException(String)`, `protected CodecException(String, Throwable)`.
  - `public class InvalidValueException extends CodecException`, `public class InvalidPayloadException extends CodecException`, `public class UnsupportedFormatException extends CodecException`, `public class TransientCodecException extends CodecException` — each with `public X(String message)` and `public X(String message, Throwable cause)`.

- [ ] **Step 1: Write the failing test**

Create `codec-core/src/test/java/org/jwcarman/codec/spi/CodecExceptionsTest.java` (license header first):

```java
package org.jwcarman.codec.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CodecExceptionsTest {

  /** One family: how to build it with and without a cause. */
  private record Family(
      Class<? extends CodecException> type,
      Function<String, CodecException> withMessage,
      BiFunction<String, Throwable, CodecException> withCause) {}

  private static final List<Family> FAMILIES =
      List.of(
          new Family(
              InvalidValueException.class,
              InvalidValueException::new,
              InvalidValueException::new),
          new Family(
              InvalidPayloadException.class,
              InvalidPayloadException::new,
              InvalidPayloadException::new),
          new Family(
              UnsupportedFormatException.class,
              UnsupportedFormatException::new,
              UnsupportedFormatException::new),
          new Family(
              TransientCodecException.class,
              TransientCodecException::new,
              TransientCodecException::new));

  @Nested
  class Every_family {

    @Test
    void is_an_unchecked_codec_exception() {
      for (Family family : FAMILIES) {
        assertThat(family.withMessage().apply("m"))
            .isInstanceOf(CodecException.class)
            .isInstanceOf(RuntimeException.class);
      }
    }

    @Test
    void preserves_its_message_and_cause() {
      Throwable cause = new IllegalStateException("underlying");
      for (Family family : FAMILIES) {
        assertThat(family.withMessage().apply("just a message"))
            .hasMessage("just a message")
            .hasNoCause();
        assertThat(family.withCause().apply("with a cause", cause))
            .hasMessage("with a cause")
            .hasCause(cause);
      }
    }

    @Test
    void is_not_a_subtype_of_any_other_family() {
      // The families are siblings keyed to different caller responses; a catch for one must
      // never accidentally take another.
      for (Family family : FAMILIES) {
        CodecException instance = family.withMessage().apply("m");
        for (Family other : FAMILIES) {
          if (other != family) {
            assertThat(instance).isNotInstanceOf(other.type());
          }
        }
      }
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -B -pl codec-core test`
Expected: COMPILATION FAILURE — none of the five classes exist.

- [ ] **Step 3: Write the five classes**

`CodecException.java` (license header first):

```java
package org.jwcarman.codec.spi;

/**
 * The root of every failure a {@link Codec} reports from {@link Codec#encode} or {@link
 * Codec#decode}.
 *
 * <p>This class is never thrown directly; it exists for the coarse {@code catch (CodecException
 * e)} — "something went wrong in the codec". Its four subclasses are keyed to what the caller does
 * next, and a catch for one of them is the precise form:
 *
 * <ul>
 *   <li>{@link InvalidValueException} — the value handed to {@code encode} cannot be encoded: fix
 *       the value or the configuration.
 *   <li>{@link InvalidPayloadException} — the payload handed to {@code decode} is malformed,
 *       corrupt, or forged: quarantine it.
 *   <li>{@link UnsupportedFormatException} — the payload is well-formed but this reader cannot
 *       handle it (a format version or algorithm it does not know): hold it, route it to a newer
 *       reader, or upgrade.
 *   <li>{@link TransientCodecException} — something the codec depends on failed; the input is not
 *       at fault: retry, or alert on infrastructure.
 * </ul>
 *
 * <p>The rule for extending the hierarchy: subclass by what the caller does next. A failure with
 * one of the four answers above is a subclass of that family, never a fifth sibling. A more
 * specific subclass earns its existence only when it carries information the family does not.
 *
 * <p>Construction and configuration errors — a bad builder argument, a null mapper, a type a
 * factory cannot create a codec for, a {@code null} passed to {@code encode} or {@code decode} —
 * are programmer errors at wiring time and remain {@link IllegalArgumentException}, {@link
 * IllegalStateException} and {@link NullPointerException}; they are not codec failures.
 */
public class CodecException extends RuntimeException {

  /**
   * Creates a codec failure with the given message; for subclasses.
   *
   * @param message what went wrong, in plain words, never containing payload contents
   */
  protected CodecException(String message) {
    super(message);
  }

  /**
   * Creates a codec failure with the given message and cause; for subclasses.
   *
   * @param message what went wrong, in plain words, never containing payload contents
   * @param cause the underlying exception, typically the wrapped library's own
   */
  protected CodecException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

`InvalidValueException.java`:

```java
package org.jwcarman.codec.spi;

/**
 * The value handed to {@link Codec#encode} cannot be encoded: a type the backend has no serializer
 * for, a cyclic object graph, a class the backend requires to be registered and is not.
 *
 * <p>The caller's response is to fix the value or the codec's configuration; retrying will not
 * help and the codec itself is healthy. The backend's own exception is preserved as the cause.
 */
public class InvalidValueException extends CodecException {

  /**
   * Creates the exception.
   *
   * @param message what about the value could not be encoded
   */
  public InvalidValueException(String message) {
    super(message);
  }

  /**
   * Creates the exception with the backend's failure as the cause.
   *
   * @param message what about the value could not be encoded
   * @param cause the backend's own exception
   */
  public InvalidValueException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

`InvalidPayloadException.java`:

```java
package org.jwcarman.codec.spi;

/**
 * The payload handed to {@link Codec#decode} is malformed, corrupt, or forged: bad framing,
 * truncation, an authentication-tag or checksum mismatch, text outside an encoding's alphabet, a
 * corrupt compressed stream, a payload that would expand past a size cap, or one that decodes to a
 * type this codec does not produce.
 *
 * <p>The caller's response is to quarantine the payload — dead-letter it, treat the cache entry
 * as a miss, reject the request — and never to retry it as is. The underlying library's exception,
 * when there is one, is preserved as the cause.
 */
public class InvalidPayloadException extends CodecException {

  /**
   * Creates the exception.
   *
   * @param message what about the payload was rejected, never including its contents
   */
  public InvalidPayloadException(String message) {
    super(message);
  }

  /**
   * Creates the exception with the underlying failure as the cause.
   *
   * @param message what about the payload was rejected, never including its contents
   * @param cause the underlying exception
   */
  public InvalidPayloadException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

`UnsupportedFormatException.java`:

```java
package org.jwcarman.codec.spi;

/**
 * The payload handed to {@link Codec#decode} is well-formed, but names a format this reader cannot
 * handle: a version or an algorithm identifier this instance was not built to understand —
 * typically because a newer writer produced it.
 *
 * <p>The data is not bad and nothing is down, so the caller's response is to hold the payload,
 * route it to a reader that understands it, or upgrade — never to quarantine it. Distinguishing
 * this from {@link InvalidPayloadException} is what makes a rolling upgrade safe: a policy that
 * dead-letters invalid payloads must not discard everything written by the instances that have
 * already been upgraded.
 */
public class UnsupportedFormatException extends CodecException {

  /**
   * Creates the exception.
   *
   * @param message which version or algorithm was not recognised
   */
  public UnsupportedFormatException(String message) {
    super(message);
  }

  /**
   * Creates the exception with an underlying cause.
   *
   * @param message which version or algorithm was not recognised
   * @param cause the underlying exception
   */
  public UnsupportedFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

`TransientCodecException.java`:

```java
package org.jwcarman.codec.spi;

/**
 * Something the codec depends on failed, and the input is not at fault: a key-management service
 * timed out or throttled, a JCE provider cannot supply a transform, a compressor failed on valid
 * input, or a provider returned something that violates its contract.
 *
 * <p>The caller's response is to retry with backoff, or to alert on infrastructure — never to
 * blame or discard the input. The name follows the precedent of Spring's {@code
 * TransientDataAccessException}; it slightly overclaims for a misconfigured dependency, which is
 * not transient, but the caller's response is the same and that is what the family is for. The
 * underlying failure is preserved as the cause whenever there is one.
 */
public class TransientCodecException extends CodecException {

  /**
   * Creates the exception.
   *
   * @param message what dependency failed
   */
  public TransientCodecException(String message) {
    super(message);
  }

  /**
   * Creates the exception with the dependency's failure as the cause.
   *
   * @param message what dependency failed
   * @param cause the dependency's own exception
   */
  public TransientCodecException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -B -pl codec-core test`
Expected: PASS, 3 new tests.

- [ ] **Step 5: Document the contract on `Codec`**

In `codec-core/src/main/java/org/jwcarman/codec/spi/Codec.java`, replace the interface Javadoc and the two abstract-method Javadocs. The current text is:

```java
/**
 * Converts values of type {@code T} to and from {@code byte[]}.
 *
 * <p>Implementations must be symmetric: {@code decode(encode(value))} yields a value equal to the
 * original. Codecs are expected to be thread-safe.
 *
 * @param <T> the type this codec converts
 */
public interface Codec<T> {

  /**
   * Encodes a value to bytes.
   *
   * @param value the value to encode
   * @return the encoded bytes
   */
  byte[] encode(T value);

  /**
   * Decodes bytes back into a value.
   *
   * @param bytes the bytes to decode
   * @return the decoded value
   */
  T decode(byte[] bytes);
```

Replace it with:

```java
/**
 * Converts values of type {@code T} to and from {@code byte[]}.
 *
 * <p>Implementations must be symmetric: {@code decode(encode(value))} yields a value equal to the
 * original. Codecs are expected to be thread-safe.
 *
 * <p><strong>Failures.</strong> Every failure {@code encode} or {@code decode} reports is a {@link
 * CodecException}, and more precisely one of four families keyed to what the caller does next:
 * {@link InvalidValueException} (fix the value), {@link InvalidPayloadException} (quarantine the
 * payload), {@link UnsupportedFormatException} (hold it for a newer reader), {@link
 * TransientCodecException} (retry). Implementations wrap their underlying library's exception as
 * the cause rather than letting it escape. A {@code null} argument is a programmer error and
 * throws {@link NullPointerException}; see {@link #nullSafe()} for the explicit pass-through.
 *
 * @param <T> the type this codec converts
 */
public interface Codec<T> {

  /**
   * Encodes a value to bytes.
   *
   * @param value the value to encode
   * @return the encoded bytes
   * @throws InvalidValueException if the value cannot be encoded by this codec
   * @throws TransientCodecException if something the codec depends on failed
   * @throws NullPointerException if {@code value} is null and this codec does not accept null
   */
  byte[] encode(T value);

  /**
   * Decodes bytes back into a value.
   *
   * @param bytes the bytes to decode
   * @return the decoded value
   * @throws InvalidPayloadException if the bytes are malformed, corrupt, or forged
   * @throws UnsupportedFormatException if the bytes are well-formed but in a format this codec
   *     cannot read
   * @throws TransientCodecException if something the codec depends on failed
   * @throws NullPointerException if {@code bytes} is null and this codec does not accept null
   */
  T decode(byte[] bytes);
```

- [ ] **Step 6: Full verification**

Run: `./mvnw -Pci -B clean verify`
Expected: BUILD SUCCESS. Nothing else in the reactor references the new classes yet.

- [ ] **Step 7: Format, apply headers, commit**

```bash
./mvnw -q spotless:apply
./mvnw -q -Plicense license:format
git add codec-core
git commit -m "codec-core: CodecException and its four families

One failure contract for encode and decode, keyed to what the caller does
next: InvalidValueException (fix the value), InvalidPayloadException
(quarantine), UnsupportedFormatException (hold for a newer reader),
TransientCodecException (retry). Codec's Javadoc now states what its two
methods throw. Spec 008."
```

---

### Task 2: `codec-transforms`, with the `codec-zstd` and `codec-lz4` tests that follow from it

**Files:**
- Modify: `codec-transforms/src/main/java/org/jwcarman/codec/transform/compress/CompressionStreamCodec.java` (`encode`, `decode`)
- Modify: `codec-transforms/src/main/java/org/jwcarman/codec/transform/checksum/ChecksumCodec.java` (`decode`, `checksumOf`)
- Modify: `codec-transforms/src/main/java/org/jwcarman/codec/transform/encoding/Base32Codec.java` (three throw sites in `decode`, lines 107, 116, 135)
- Modify: `codec-transforms/src/main/java/org/jwcarman/codec/transform/encoding/Base64Codec.java` (`decode`)
- Modify: `codec-transforms/src/main/java/org/jwcarman/codec/transform/encoding/HexCodec.java` (`decode`)
- Modify: `codec-transforms/src/main/java/org/jwcarman/codec/transform/text/StringCodec.java` (`decode`)
- Test (modify): `CompressionStreamCodecTest`, `GzipCodecTest`, `DeflateCodecTest`, `ChecksumCodecTest`, `Base32CodecTest`, `Base64CodecTest`, `HexCodecTest`, `StringCodecTest` under `codec-transforms/src/test/java/org/jwcarman/codec/transform/...`
- Test (modify): `codec-zstd/src/test/java/org/jwcarman/codec/zstd/ZstdCodecTest.java`, `codec-lz4/src/test/java/org/jwcarman/codec/lz4/Lz4CodecTest.java`

**Interfaces:**
- Consumes: `InvalidPayloadException(String, Throwable)`, `InvalidPayloadException(String)`, `TransientCodecException(String, Throwable)`, `TransientCodecException(String)` from Task 1.
- Produces: nothing new; behaviour only.

`StringCodec.encode` does **not** change: it uses `String.getBytes(charset)`, which replaces unmappable characters rather than reporting them, and that existing behaviour is out of this spec's scope. This resolves the hedge in spec 008's transforms table — no `InvalidValueException` is thrown by `StringCodec`.

- [ ] **Step 1: Rewrite the test assertions to the new families**

Make every change below, then run the module tests and confirm they FAIL (the production code still throws the old types). Add `import org.jwcarman.codec.spi.InvalidPayloadException;` (and `TransientCodecException` where used) to each file; remove `assertThatIllegalArgumentException`/`UncheckedIOException`/`IllegalStateException` imports that become unused. Keep every `withMessageContaining(...)` exactly as it is.

`CompressionStreamCodecTest` — replace the two wrapping tests:

```java
  @Test
  void encode_reports_a_failing_compressor_as_transient() {
    assertThatExceptionOfType(TransientCodecException.class)
        .isThrownBy(() -> codec.encode(new byte[] {1, 2, 3}))
        .withMessage("Unable to compress data")
        .withCauseInstanceOf(IOException.class);
  }

  @Test
  void decode_reports_a_corrupt_stream_as_an_invalid_payload() {
    assertThatExceptionOfType(InvalidPayloadException.class)
        .isThrownBy(() -> codec.decode(new byte[] {1, 2, 3}))
        .withMessage("Unable to decompress data")
        .withCauseInstanceOf(IOException.class);
  }
```

`GzipCodecTest`, `DeflateCodecTest`, `ZstdCodecTest`, `Lz4CodecTest` — in each, every `assertThatExceptionOfType(UncheckedIOException.class)` on a `decode` call becomes `assertThatExceptionOfType(InvalidPayloadException.class)`, and every `assertThatExceptionOfType(IllegalStateException.class)` in the `Decoded_size_cap` nested class becomes `assertThatExceptionOfType(InvalidPayloadException.class)`. Concretely:

| File | Lines | Old | New |
|---|---|---|---|
| `GzipCodecTest` | 75 | `UncheckedIOException.class` | `InvalidPayloadException.class` |
| `GzipCodecTest` | 87 | `IllegalStateException.class` | `InvalidPayloadException.class` |
| `DeflateCodecTest` | 84 | `UncheckedIOException.class` | `InvalidPayloadException.class` |
| `DeflateCodecTest` | 96 | `IllegalStateException.class` | `InvalidPayloadException.class` |
| `ZstdCodecTest` | 88 | `UncheckedIOException.class` | `InvalidPayloadException.class` |
| `ZstdCodecTest` | 101 | `IllegalStateException.class` | `InvalidPayloadException.class` |
| `Lz4CodecTest` | 98, 107 | `UncheckedIOException.class` | `InvalidPayloadException.class` |
| `Lz4CodecTest` | 119 | `IllegalStateException.class` | `InvalidPayloadException.class` |

Do not touch the constructor-argument tests in those files (`new GzipCodec(0)`, `new Lz4Codec(0)`, `new ZstdCodec(3, 0)`, level checks) — those stay `IllegalArgumentException`.

`ChecksumCodecTest` — the five `assertThatIllegalArgumentException()` calls at lines 101, 111, 119, 126 and 135 (all on `decode`) become `assertThatExceptionOfType(InvalidPayloadException.class)`; the `assertThatIllegalStateException()` at line 174 (the 64-bit checksum, on `encode`) becomes `assertThatExceptionOfType(TransientCodecException.class)`. Leave line-numbered messages (`"corrupt"`, `"shorter"`, `"wider than 32 bits"`) as they are.

`Base32CodecTest` — lines 124, 134, 144, 153, 160: `assertThatIllegalArgumentException()` → `assertThatExceptionOfType(InvalidPayloadException.class)`.

`Base64CodecTest` — line 106 becomes:

```java
      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> Base64Codec.basic().decode("not*base64!".getBytes(US_ASCII)))
          .withMessage("Input is not valid Base64")
          .withCauseInstanceOf(IllegalArgumentException.class);
```

`HexCodecTest` — lines 90 and 97 become, respectively:

```java
      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> HexCodec.lowerCase().decode(odd))
          .withMessage("Input is not valid hex")
          .withCauseInstanceOf(IllegalArgumentException.class);
```

```java
      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> HexCodec.lowerCase().decode(bad))
          .withMessage("Input is not valid hex")
          .withCauseInstanceOf(IllegalArgumentException.class);
```

`StringCodecTest` — lines 89 and 98: `assertThatIllegalArgumentException()` → `assertThatExceptionOfType(InvalidPayloadException.class)`; on the first, add `.withCauseInstanceOf(java.nio.charset.CharacterCodingException.class)` after the existing `.withMessageContaining("UTF-8")`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -B -pl codec-transforms,codec-zstd,codec-lz4 -am test`
Expected: FAIL — every rewritten assertion reports the old exception type was thrown.

- [ ] **Step 3: Rewrite the production throw sites**

`CompressionStreamCodec.java` — replace the two catch blocks and the cap throw. The `encode` method becomes:

```java
  @Override
  public final byte[] encode(byte[] value) {
    Objects.requireNonNull(value, "value must not be null");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (OutputStream compress = compressing(out)) {
      compress.write(value);
    } catch (IOException e) {
      // A compressor failing on valid input is the dependency's fault, not the value's.
      throw new TransientCodecException("Unable to compress data", e);
    }
    return out.toByteArray();
  }
```

and `decode` becomes:

```java
  @Override
  public final byte[] decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    try (InputStream decompress = decompressing(new ByteArrayInputStream(bytes))) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[BUFFER_SIZE];
      long total = 0;
      int read;
      while ((read = decompress.read(buffer)) != -1) {
        total += read;
        if (total > maxDecodedSize) {
          // A payload that expands past the cap is hostile input, not a resource problem.
          throw new InvalidPayloadException(
              "Decoded size exceeds the maximum of " + maxDecodedSize + " bytes");
        }
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new InvalidPayloadException("Unable to decompress data", e);
    }
  }
```

Replace `import java.io.UncheckedIOException;` with `import org.jwcarman.codec.spi.InvalidPayloadException;` and `import org.jwcarman.codec.spi.TransientCodecException;`. Update the class Javadoc wherever it names `IllegalStateException` or `UncheckedIOException` for these two outcomes (the decompression-bomb paragraph says "throwing `IllegalStateException`"; make it `InvalidPayloadException`).

`ChecksumCodec.java` — in `decode`, both `new IllegalArgumentException(` become `new InvalidPayloadException(` (messages unchanged); in `checksumOf`, `new IllegalStateException(` becomes `new TransientCodecException(` (message unchanged). Add the two imports.

`Base32Codec.java` — the three `new IllegalArgumentException(` in `decode` (lines 107, 116, 135) become `new InvalidPayloadException(`, messages unchanged. Add the import.

`Base64Codec.java` — `decode` becomes:

```java
  @Override
  public byte[] decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    try {
      return decoder.decode(bytes);
    } catch (IllegalArgumentException e) {
      throw new InvalidPayloadException("Input is not valid Base64", e);
    }
  }
```

`HexCodec.java` — `decode` becomes:

```java
  @Override
  public byte[] decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    try {
      return format.parseHex(new String(bytes, StandardCharsets.US_ASCII));
    } catch (IllegalArgumentException e) {
      throw new InvalidPayloadException("Input is not valid hex", e);
    }
  }
```

`StringCodec.java` — in `decode`, `throw new IllegalArgumentException("Input is not valid " + charset.name(), e);` becomes `throw new InvalidPayloadException("Input is not valid " + charset.name(), e);`. Add the import.

In every one of these classes, update any Javadoc `@throws IllegalArgumentException` / `@throws IllegalStateException` on `encode`/`decode` to the new family with the same description.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -B -pl codec-transforms,codec-zstd,codec-lz4 -am test`
Expected: PASS.

- [ ] **Step 5: Full verification, format, commit**

Run: `./mvnw -Pci -B clean verify` — expected BUILD SUCCESS.

```bash
./mvnw -q spotless:apply
git add codec-transforms codec-zstd codec-lz4
git commit -m "Transforms: report failures through the codec exception families

Corrupt compressed streams, a payload past the decompression cap, checksum
mismatches, text outside an encoding's alphabet and undecodable charset
input are all InvalidPayloadException; a compressor or checksum that fails
on valid input is TransientCodecException. Library exceptions ride along as
the cause. Messages are unchanged. Spec 008."
```

---

### Task 3: The JSON backends — `codec-jackson`, `codec-jackson2`, `codec-gson`, `codec-jsonb`

**Files:**
- Modify: `codec-jackson/src/main/java/org/jwcarman/codec/jackson/JacksonCodec.java`
- Modify: `codec-jackson2/src/main/java/org/jwcarman/codec/jackson2/Jackson2Codec.java`
- Modify: `codec-gson/src/main/java/org/jwcarman/codec/gson/GsonCodec.java`
- Modify: `codec-jsonb/src/main/java/org/jwcarman/codec/jsonb/JsonbCodec.java`
- Modify: `codec-jsonb/pom.xml` (declare `jakarta.json:jakarta.json-api`)
- Test: `codec-jackson/src/test/java/org/jwcarman/codec/jackson/JacksonCodecFactoryTest.java` (add two `should*` tests)
- Test: `codec-jackson2/src/test/java/org/jwcarman/codec/jackson2/Jackson2CodecFactoryTest.java` (rewrite lines 93–112)
- Test: `codec-gson/src/test/java/org/jwcarman/codec/gson/GsonCodecFactoryTest.java` (add two `should*` tests)
- Test: `codec-jsonb/src/test/java/org/jwcarman/codec/jsonb/JsonbCodecFactoryTest.java` (rewrite the `Failures` nested class)

**Interfaces:**
- Consumes: `InvalidValueException(String, Throwable)`, `InvalidPayloadException(String, Throwable)` from Task 1.
- Produces: nothing new.

Verified library behaviour this task relies on (probed against the versions in the reactor): Jackson 3 throws `tools.jackson.core.JacksonException` (a `RuntimeException`) for both directions — a self-referencing bean on encode raises its `InvalidDefinitionException`, garbage on decode its `StreamReadException`; Jackson 3 does **not** fail on `new Object()` (it writes `{}`), unlike Jackson 2. Jackson 2's `JacksonException` extends `IOException`. Gson's root is `com.google.gson.JsonParseException` (`JsonSyntaxException` on decode, `JsonIOException` when a `TypeAdapter` throws `IOException` on encode); Gson refuses `java.lang.Class` with `UnsupportedOperationException`, which is *not* wrapped — spec 008 says wrap the library root only. JSON-B's root is `jakarta.json.bind.JsonbException`; Yasson wraps JSON-P's `JsonParsingException` in it, Johnzon does not, so decode catches `jakarta.json.JsonException` too.

- [ ] **Step 1: Write the failing tests**

`JacksonCodecFactoryTest` — this file uses camelCase `should*` names and no nesting; match it. Add after `shouldRejectNullTypeRef`:

```java
  /** A bean that refers to itself: Jackson 3 refuses to serialize the cycle. */
  static final class SelfReferencing {
    private SelfReferencing next;

    public SelfReferencing getNext() {
      return next;
    }
  }

  @Test
  void shouldReportAnUnencodableValueAsInvalidValue() {
    SelfReferencing cycle = new SelfReferencing();
    cycle.next = cycle;
    Codec<SelfReferencing> codec = factory.create(SelfReferencing.class);

    assertThatExceptionOfType(InvalidValueException.class)
        .isThrownBy(() -> codec.encode(cycle))
        .withMessage("Unable to encode value as JSON")
        .withCauseInstanceOf(JacksonException.class);
  }

  @Test
  void shouldReportMalformedJsonAsInvalidPayload() {
    Codec<Person> codec = factory.create(Person.class);

    assertThatExceptionOfType(InvalidPayloadException.class)
        .isThrownBy(() -> codec.decode("not json".getBytes(UTF_8)))
        .withMessage("Unable to decode JSON")
        .withCauseInstanceOf(JacksonException.class);
  }
```

Imports to add: `import static java.nio.charset.StandardCharsets.UTF_8;`, `import static org.assertj.core.api.Assertions.assertThatExceptionOfType;`, `import org.jwcarman.codec.spi.InvalidPayloadException;`, `import org.jwcarman.codec.spi.InvalidValueException;`, `import tools.jackson.core.JacksonException;`.

`Jackson2CodecFactoryTest` — replace the two nested classes `Encoding_unsupported_values` and `Decoding_invalid_input` (lines 90–113) with:

```java
  @Nested
  class Encoding_unsupported_values {

    @Test
    void reports_an_unencodable_value_as_invalid_value() {
      Codec<Object> codec = factory.create(Object.class);
      Object unserializable = new Object();

      assertThatExceptionOfType(InvalidValueException.class)
          .isThrownBy(() -> codec.encode(unserializable))
          .withMessage("Unable to encode value as JSON")
          .withCauseInstanceOf(JsonProcessingException.class);
    }
  }

  @Nested
  class Decoding_invalid_input {

    @Test
    void reports_malformed_json_as_invalid_payload() {
      Codec<Person> codec = factory.create(Person.class);
      byte[] garbage = "not json".getBytes(StandardCharsets.UTF_8);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(garbage))
          .withMessage("Unable to decode JSON")
          .withCauseInstanceOf(IOException.class);
    }
  }
```

Imports: add `com.fasterxml.jackson.core.JsonProcessingException`, `java.io.IOException`, `org.jwcarman.codec.spi.InvalidPayloadException`, `org.jwcarman.codec.spi.InvalidValueException`; remove `java.io.UncheckedIOException`.

`GsonCodecFactoryTest` — camelCase, no nesting. Add after `shouldRejectNullTypeRef`:

```java
  @Test
  void shouldReportAFailingAdapterAsInvalidValue() {
    TypeAdapter<Person> failing =
        new TypeAdapter<>() {
          @Override
          public void write(JsonWriter out, Person value) throws IOException {
            throw new IOException("adapter refused");
          }

          @Override
          public Person read(JsonReader in) {
            return null;
          }
        };
    Gson gson = new GsonBuilder().registerTypeAdapter(Person.class, failing).create();
    Codec<Person> codec = new GsonCodecFactory(gson).create(Person.class);

    assertThatExceptionOfType(InvalidValueException.class)
        .isThrownBy(() -> codec.encode(new Person("Alice", 30, true)))
        .withMessage("Unable to encode value as JSON")
        .withCauseInstanceOf(JsonIOException.class);
  }

  @Test
  void shouldReportMalformedJsonAsInvalidPayload() {
    Codec<Person> codec = factory.create(Person.class);

    assertThatExceptionOfType(InvalidPayloadException.class)
        .isThrownBy(() -> codec.decode("not json".getBytes(UTF_8)))
        .withMessage("Unable to decode JSON")
        .withCauseInstanceOf(JsonSyntaxException.class);
  }
```

Imports: `static java.nio.charset.StandardCharsets.UTF_8`, `static org.assertj.core.api.Assertions.assertThatExceptionOfType`, `com.google.gson.GsonBuilder`, `com.google.gson.JsonIOException`, `com.google.gson.JsonSyntaxException`, `com.google.gson.TypeAdapter`, `com.google.gson.stream.JsonReader`, `com.google.gson.stream.JsonWriter`, `java.io.IOException`, `org.jwcarman.codec.spi.InvalidPayloadException`, `org.jwcarman.codec.spi.InvalidValueException`.

`JsonbCodecFactoryTest` — replace the `Failures` nested class (from line 130) with:

```java
  @Nested
  class Failures {

    /** A getter that throws: Yasson reports it as a JsonbException. */
    public static class ThrowingGetter {
      public String getBoom() {
        throw new IllegalStateException("boom");
      }
    }

    @Test
    void malformed_json_is_an_invalid_payload() {
      Codec<Person> codec = factory.create(Person.class);
      byte[] notJson = "not json".getBytes(UTF_8);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(notJson))
          .withMessage("Unable to decode JSON")
          .withCauseInstanceOf(JsonbException.class);
    }

    @Test
    void a_value_the_binding_cannot_serialize_is_an_invalid_value() {
      Codec<ThrowingGetter> codec = factory.create(ThrowingGetter.class);

      assertThatExceptionOfType(InvalidValueException.class)
          .isThrownBy(() -> codec.encode(new ThrowingGetter()))
          .withMessage("Unable to encode value as JSON")
          .withCauseInstanceOf(JsonbException.class);
    }
```

(Keep whatever other tests the original `Failures` class held after `invalid_json_surfaces_as_a_json_b_exception` — only that one test is replaced by the two above.) Imports: `org.jwcarman.codec.spi.InvalidPayloadException`, `org.jwcarman.codec.spi.InvalidValueException`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -B -pl codec-jackson,codec-jackson2,codec-gson,codec-jsonb -am test`
Expected: FAIL in all four modules.

- [ ] **Step 3: Rewrite the four codecs**

`JacksonCodec.java` (Jackson 3):

```java
package org.jwcarman.codec.jackson;

import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;
import org.jwcarman.codec.spi.InvalidValueException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

class JacksonCodec<T> implements Codec<T> {

  private final ObjectMapper objectMapper;
  private final JavaType javaType;

  JacksonCodec(ObjectMapper objectMapper, JavaType javaType) {
    this.objectMapper = objectMapper;
    this.javaType = javaType;
  }

  @Override
  public byte[] encode(T value) {
    try {
      return objectMapper.writeValueAsBytes(value);
    } catch (JacksonException e) {
      throw new InvalidValueException("Unable to encode value as JSON", e);
    }
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return objectMapper.readValue(bytes, javaType);
    } catch (JacksonException e) {
      throw new InvalidPayloadException("Unable to decode JSON", e);
    }
  }
}
```

`Jackson2Codec.java` — replace the two catch blocks:

```java
  @Override
  public byte[] encode(T value) {
    try {
      return objectMapper.writeValueAsBytes(value);
    } catch (JsonProcessingException e) {
      throw new InvalidValueException("Unable to encode value as JSON", e);
    }
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return objectMapper.readValue(bytes, javaType);
    } catch (IOException e) {
      throw new InvalidPayloadException("Unable to decode JSON", e);
    }
  }
```

Replace `import java.io.UncheckedIOException;` with the two `org.jwcarman.codec.spi` imports.

`GsonCodec.java`:

```java
  @Override
  public byte[] encode(T value) {
    try {
      return gson.toJson(value).getBytes(StandardCharsets.UTF_8);
    } catch (JsonParseException e) {
      throw new InvalidValueException("Unable to encode value as JSON", e);
    }
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return gson.fromJson(new String(bytes, StandardCharsets.UTF_8), typeToken);
    } catch (JsonParseException e) {
      throw new InvalidPayloadException("Unable to decode JSON", e);
    }
  }
```

Add `import com.google.gson.JsonParseException;` and the two `spi` imports.

`JsonbCodec.java`:

```java
  @Override
  public byte[] encode(T value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      jsonb.toJson(value, type, out);
    } catch (JsonbException e) {
      throw new InvalidValueException("Unable to encode value as JSON", e);
    }
    return out.toByteArray();
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return jsonb.fromJson(new ByteArrayInputStream(bytes), type);
    } catch (JsonbException | JsonException e) {
      // Yasson wraps JSON-P's parse failure in JsonbException; Johnzon lets it through as-is.
      throw new InvalidPayloadException("Unable to decode JSON", e);
    }
  }
```

Add `import jakarta.json.JsonException;`, `import jakarta.json.bind.JsonbException;` and the two `spi` imports.

`codec-jsonb/pom.xml` — `jakarta.json.JsonException` lives in `jakarta.json:jakarta.json-api`, which was a transitive of the JSON-B API; referencing it directly makes it a used-undeclared dependency and the ci analyzer will fail the build. Declare it, directly after the `jakarta.json.bind-api` dependency, with no version (Spring Boot's BOM manages it):

```xml
        <dependency>
            <groupId>jakarta.json</groupId>
            <artifactId>jakarta.json-api</artifactId>
        </dependency>
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -B -pl codec-jackson,codec-jackson2,codec-gson,codec-jsonb -am test`
Expected: PASS. If Yasson logs a `SEVERE: Generating incomplete JSON` line during the throwing-getter test, that is Yasson's own logging on the expected failure path, not a problem.

- [ ] **Step 5: Full verification, format, commit**

Run: `./mvnw -Pci -B clean verify` — BUILD SUCCESS, and in particular the `codec-jsonb` dependency analysis passes with the new declaration.

```bash
./mvnw -q spotless:apply
git add codec-jackson codec-jackson2 codec-gson codec-jsonb
git commit -m "JSON backends: report failures through the codec exception families

Jackson 3, Jackson 2, Gson and JSON-B wrap their library's parse failure as
InvalidPayloadException and its serialization failure as
InvalidValueException, with the library exception as the cause. No library
type escapes decode any more; JSON-B's Yasson/Johnzon difference on parse
errors is absorbed. Spec 008."
```

---

### Task 4: `codec-protobuf` and `codec-fory`

**Files:**
- Modify: `codec-protobuf/src/main/java/org/jwcarman/codec/protobuf/ProtobufCodec.java` (`decode`)
- Modify: `codec-fory/src/main/java/org/jwcarman/codec/fory/ForyCodec.java` (`encode`, `decode`)
- Test: `codec-protobuf/src/test/java/org/jwcarman/codec/protobuf/ProtobufCodecFactoryTest.java` (`shouldThrowForInvalidBytes`, lines 106–113)
- Test: `codec-fory/src/test/java/org/jwcarman/codec/fory/ForyCodecFactoryTest.java` (`Security_boundary` lines 196–218; `Failures` lines 292–311; add one encode test)

**Interfaces:**
- Consumes: `InvalidValueException`, `InvalidPayloadException` from Task 1.
- Produces: nothing new.

Verified Fory behaviour (probed against 1.7.1): an unregistered class on `serialize` raises `InsecureException` (a `ForyException`); on `deserialize`, a truncated payload raises `DeserializationException` (a `ForyException`), an empty buffer raises `IndexOutOfBoundsException`, and a one-byte or "not fory" buffer raises `IllegalArgumentException`. Fory has no integrity check, so a bit-flipped payload may decode "successfully" to a wrong value — that is a property of the format, not this task's concern. Because malformed input reaches the codec as three unrelated types, `ForyCodec.decode` is the one sanctioned deviation from "library root only": it catches all three, and says so in a comment.

- [ ] **Step 1: Write the failing tests**

`ProtobufCodecFactoryTest` — replace `shouldThrowForInvalidBytes` (camelCase file) with:

```java
  @Test
  void shouldReportInvalidBytesAsInvalidPayload() {
    Codec<TestMessages.Person> codec = factory.create(TestMessages.Person.class);
    byte[] garbage = {0x00, 0x7F, 0x00, 0x7F, 0x00};
    assertThatThrownBy(() -> codec.decode(garbage))
        .isInstanceOf(InvalidPayloadException.class)
        .hasMessageContaining("Failed to decode protobuf message")
        .hasCauseInstanceOf(InvalidProtocolBufferException.class);
  }
```

Imports: `com.google.protobuf.InvalidProtocolBufferException`, `org.jwcarman.codec.spi.InvalidPayloadException`.

`ForyCodecFactoryTest` — in `Security_boundary`, the three assertions change type but keep their causes visible:

```java
    @Test
    void a_payload_naming_an_unregistered_class_is_rejected_not_materialised() {
      byte[] bytes =
          ForyCodecFactory.of(Schema_evolution.Evolved.class)
              .create(Schema_evolution.Evolved.class)
              .encode(new Schema_evolution.Evolved("Alice", 30, true, "alice@example.com"));
      ForyCodecFactory reader = ForyCodecFactory.of();

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> reader.create(Object.class).decode(bytes))
          .withCauseInstanceOf(DeserializationException.class);
    }

    @Test
    void a_graph_deeper_than_the_read_limit_is_rejected() {
      Codec<Node> codec = ForyCodecFactory.of(Node.class).create(Node.class);
      byte[] bytes = codec.encode(chain(60));

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(bytes))
          .withCauseInstanceOf(InsecureException.class)
          .havingCause()
          .withMessageContaining("depth");
    }

    @Test
    void a_rejected_read_does_not_poison_the_instance() {
      Codec<Node> codec = ForyCodecFactory.of(Node.class).create(Node.class);
      byte[] tooDeep = codec.encode(chain(60));
      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(tooDeep));

      Node shallow = chain(10);

      assertThat(codec.decode(codec.encode(shallow))).isEqualTo(shallow);
    }
```

In `Failures`, replace the first two tests and add a third:

```java
    @Test
    void decoding_a_value_of_the_wrong_type_is_an_invalid_payload() {
      byte[] person = factory.create(Person.class).encode(new Person("Alice", 30, true));
      Codec<Order> orders = factory.create(Order.class);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> orders.decode(person))
          .withMessageContaining("Person")
          .withMessageContaining("Order");
    }

    @Test
    void corrupt_input_is_an_invalid_payload_whichever_way_fory_reports_it() {
      Codec<Person> codec = factory.create(Person.class);
      byte[] valid = codec.encode(new Person("Alice", 30, true));

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode("not fory".getBytes(UTF_8)))
          .withCauseInstanceOf(IllegalArgumentException.class);
      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(new byte[0]))
          .withCauseInstanceOf(IndexOutOfBoundsException.class);
      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> codec.decode(java.util.Arrays.copyOf(valid, valid.length / 2)))
          .withCauseInstanceOf(DeserializationException.class);
    }

    @Test
    void encoding_a_value_of_an_unregistered_class_is_an_invalid_value() {
      Codec<Object> codec = factory.create(Object.class);

      assertThatExceptionOfType(InvalidValueException.class)
          .isThrownBy(() -> codec.encode(new Unregistered("x")))
          .withMessage("Unable to serialize value")
          .withCauseInstanceOf(InsecureException.class);
    }
```

Imports to add: `org.jwcarman.codec.spi.InvalidPayloadException`, `org.jwcarman.codec.spi.InvalidValueException`; `assertThatRuntimeException` becomes unused — remove its import. `assertThatExceptionOfType(ClassCastException.class)` no longer appears.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -B -pl codec-protobuf,codec-fory -am test`
Expected: FAIL in both.

- [ ] **Step 3: Rewrite the two codecs**

`ProtobufCodec.java` — in `decode`, `throw new IllegalArgumentException("Failed to decode protobuf message", e);` becomes `throw new InvalidPayloadException("Failed to decode protobuf message", e);`. Add the import.

`ForyCodec.java`:

```java
package org.jwcarman.codec.fory;

import org.apache.fory.ThreadSafeFory;
import org.apache.fory.exception.ForyException;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;
import org.jwcarman.codec.spi.InvalidValueException;

class ForyCodec<T> implements Codec<T> {

  private final ThreadSafeFory fory;
  private final Class<?> rawType;

  ForyCodec(ThreadSafeFory fory, Class<?> rawType) {
    this.fory = fory;
    this.rawType = rawType;
  }

  @Override
  public byte[] encode(T value) {
    try {
      return fory.serialize(value);
    } catch (ForyException e) {
      throw new InvalidValueException("Unable to serialize value", e);
    }
  }

  @Override
  public T decode(byte[] bytes) {
    Object value;
    try {
      value = fory.deserialize(bytes);
    } catch (ForyException | IllegalArgumentException | IndexOutOfBoundsException e) {
      // Fory reports malformed input three ways: its own exceptions for truncation and
      // security-limit violations, IllegalArgumentException for a buffer it cannot parse at all,
      // IndexOutOfBoundsException for an empty one. All three mean the same thing here.
      throw new InvalidPayloadException("Unable to deserialize payload", e);
    }
    if (value != null && !rawType.isInstance(value)) {
      throw new InvalidPayloadException(
          "Decoded a " + value.getClass().getName() + " but expected " + rawType.getName());
    }
    return (T) value;
  }
}
```

Update `ForyCodec`'s class Javadoc sentence about failures ("runtime exceptions surface unchanged: an unregistered class, a corrupt payload, or a value of the wrong type") to say they surface as `InvalidValueException` / `InvalidPayloadException` with Fory's exception as the cause, and the matching sentence in `ForyCodecFactory`'s class Javadoc if it repeats the claim.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -B -pl codec-protobuf,codec-fory -am test`
Expected: PASS.

- [ ] **Step 5: Full verification, format, commit**

Run: `./mvnw -Pci -B clean verify` — BUILD SUCCESS.

```bash
./mvnw -q spotless:apply
git add codec-protobuf codec-fory
git commit -m "Protobuf and Fory: report failures through the codec exception families

A protobuf parse failure is InvalidPayloadException. Fory's three ways of
reporting malformed input — its own exceptions, IllegalArgumentException
and IndexOutOfBoundsException — all become InvalidPayloadException, as does
a payload that decodes to the wrong type; an unregistered class on encode is
InvalidValueException. Spec 008."
```

---

### Task 5: `codec-versioned`

**Files:**
- Modify: `codec-versioned/src/main/java/org/jwcarman/codec/versioned/VersionedFormatException.java`
- Modify: `codec-versioned/src/main/java/org/jwcarman/codec/versioned/UnknownVersionException.java`
- Modify: `codec-versioned/src/main/java/org/jwcarman/codec/versioned/VersionedCodec.java` (the "Failures" Javadoc paragraph, lines 70–76)
- Modify: `docs/superpowers/specs/007-codec-versioned.md` (§Errors — amendment note)
- Test: `codec-versioned/src/test/java/org/jwcarman/codec/versioned/UnknownVersionExceptionTest.java` (line 47)
- Test: `codec-versioned/src/test/java/org/jwcarman/codec/versioned/VersionedCodecTest.java` (add one assertion)

**Interfaces:**
- Consumes: `InvalidPayloadException(String)`, `UnsupportedFormatException(String)` from Task 1.
- Produces: `VersionedFormatException extends InvalidPayloadException`; `UnknownVersionException extends UnsupportedFormatException` (still with `version()`), **no longer** a `VersionedFormatException`.

- [ ] **Step 1: Write the failing tests**

`UnknownVersionExceptionTest` — replace `is_a_versioned_format_exception` with:

```java
    @Test
    void is_an_unsupported_format_not_an_invalid_payload() {
      // A newer writer's output is fine data this reader cannot handle yet; a policy that
      // dead-letters invalid payloads must never take it.
      assertThat(new UnknownVersionException(7))
          .isInstanceOf(UnsupportedFormatException.class)
          .isNotInstanceOf(VersionedFormatException.class)
          .isNotInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void the_framing_failure_is_an_invalid_payload() {
      assertThat(new VersionedFormatException("bad magic"))
          .isInstanceOf(InvalidPayloadException.class)
          .isNotInstanceOf(UnsupportedFormatException.class);
    }
```

Imports: `org.jwcarman.codec.spi.InvalidPayloadException`, `org.jwcarman.codec.spi.UnsupportedFormatException`.

`VersionedCodecTest` — in `A_version_with_no_registered_codec.is_rejected_naming_the_version`, add `.isNotInstanceOf(VersionedFormatException.class)` after the existing `.satisfies(...)`, so the decode path — not just the constructor — is pinned to the new shape.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -B -pl codec-versioned -am test`
Expected: FAIL — `UnknownVersionException` is still a `VersionedFormatException`, and neither is a `CodecException`.

- [ ] **Step 3: Re-parent the two exceptions**

`VersionedFormatException.java` — change `extends RuntimeException` to `extends InvalidPayloadException`, add the import, and replace the class Javadoc with:

```java
/**
 * Signals that a buffer handed to a versioned codec is not a well-formed versioned payload: it is
 * shorter than the three-byte header, or it does not carry the versioned magic.
 *
 * <p>This is the "these bytes were never ours" failure — a codec pointed at data some other codec
 * wrote — and so an {@link InvalidPayloadException}: quarantine it. Contrast {@link
 * UnknownVersionException}, which means the framing is ours but the version is one this codec does
 * not know; that is an {@link org.jwcarman.codec.spi.UnsupportedFormatException}, and the two
 * deliberately share no parent below {@link org.jwcarman.codec.spi.CodecException}.
 */
```

`UnknownVersionException.java` — change `extends VersionedFormatException` to `extends UnsupportedFormatException`, add the import, and replace the class Javadoc with:

```java
/**
 * Signals that a payload carries valid versioned framing but names a version this codec has no
 * codec registered for.
 *
 * <p>During a rollout this is the "written by a newer deploy" signal: the data is fine, and the
 * caller should hold it or route it to a newer reader rather than treat it as corrupt. It is an
 * {@link UnsupportedFormatException} for exactly that reason, and deliberately <em>not</em> a
 * {@link VersionedFormatException} — a policy that dead-letters invalid payloads must not discard
 * every message written by the instances that have already been upgraded.
 */
```

`VersionedCodec.java` — replace the "Failures" paragraph:

```java
 * <p><strong>Failures.</strong> {@code decode} throws {@link VersionedFormatException} (an {@link
 * org.jwcarman.codec.spi.InvalidPayloadException}) when the buffer is shorter than the three-byte
 * header or the magic does not match — bytes some other codec wrote — and {@link
 * UnknownVersionException} (an {@link org.jwcarman.codec.spi.UnsupportedFormatException},
 * carrying the version) when the framing is valid but names a version this codec has no
 * registration for. The two share no parent below {@link org.jwcarman.codec.spi.CodecException},
 * so a policy that quarantines invalid payloads cannot accidentally discard a newer writer's
 * output. Exceptions thrown by a delegate codec propagate unchanged. {@code encode} throws {@link
 * NullPointerException} on a {@code null} value; wrap the built codec with {@link Codec#nullSafe()}
 * to opt into passing {@code null} straight through instead.
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -B -pl codec-versioned -am test`
Expected: PASS. The existing `is_not_reported_as_an_unknown_version` test still holds.

- [ ] **Step 5: Amend spec 007**

In `docs/superpowers/specs/007-codec-versioned.md`, at the end of the `### Errors` subsection, add:

```markdown
Amended 2026-09-06 by spec 008: `VersionedFormatException` extends
`InvalidPayloadException` and `UnknownVersionException` extends
`UnsupportedFormatException`. `UnknownVersionException` is **no longer** a
subtype of `VersionedFormatException` — that inheritance would have let a
"quarantine invalid payloads" policy discard every message written by a newer
deploy, which is the failure this module exists to prevent.
```

- [ ] **Step 6: Full verification, format, commit**

Run: `./mvnw -Pci -B clean verify` — BUILD SUCCESS.

```bash
./mvnw -q spotless:apply
git add codec-versioned docs/superpowers/specs/007-codec-versioned.md
git commit -m "codec-versioned: framing failures are invalid payloads, unknown versions are unsupported formats

UnknownVersionException is no longer a VersionedFormatException. A policy
that dead-letters InvalidPayloadException would otherwise have discarded
every message from an already-upgraded writer during a rollout — the case
the module was built for. Spec 008."
```

---

### Task 6: `codec-crypto`

**Files:**
- Modify: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/DecryptionException.java` (superclass, Javadoc)
- Modify: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/KeyAccessException.java` (superclass, Javadoc)
- Modify: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/EncryptionException.java` (superclass, Javadoc)
- Modify: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/EnvelopeCodec.java` (lines 188–193: unknown version / algorithm id)
- Modify: `docs/superpowers/specs/005-codec-crypto.md` (§Error handling), `docs/superpowers/specs/006-codec-crypto-assurance.md` (§2.2 decode invariant)
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/ExceptionTaxonomyTest.java` (rewrite)
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/EnvelopeCodecDecodeTest.java` (new nested class)
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/EnvelopeCodecDecodeFuzzTest.java`, `EnvelopeCodecMutationFuzzTest.java` (the catch clause)

**Interfaces:**
- Consumes: `InvalidPayloadException`, `UnsupportedFormatException`, `TransientCodecException` from Task 1.
- Produces: `DecryptionException extends InvalidPayloadException`; `KeyAccessException extends TransientCodecException`; `EncryptionException extends TransientCodecException`. Unknown format version and unknown algorithm id throw the base `UnsupportedFormatException`.

PIT runs on this module at an 85% mutation threshold and the current score is 100%; the changes here add no branches, only types, so the score must not move.

- [ ] **Step 1: Write the failing tests**

`ExceptionTaxonomyTest` — replace the whole class body:

```java
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExceptionTaxonomyTest {

  @Test
  void decryption_exception_is_an_invalid_payload() {
    assertThat(new DecryptionException("bad magic"))
        .isInstanceOf(InvalidPayloadException.class)
        .isNotInstanceOf(TransientCodecException.class)
        .isNotInstanceOf(UnsupportedFormatException.class);
  }

  @Test
  void cryptographic_failures_share_one_uniform_message() {
    var cause = new RuntimeException("tag mismatch detail");
    DecryptionException e = DecryptionException.cryptographic(cause);
    assertThat(e).hasMessage("Unable to decrypt data").hasCause(cause);
  }

  @Test
  void key_access_exception_is_transient_preserving_cause() {
    var cause = new RuntimeException("kms timeout");
    assertThat(new KeyAccessException("key infrastructure unavailable", cause))
        .isInstanceOf(TransientCodecException.class)
        .isNotInstanceOf(InvalidPayloadException.class)
        .hasCause(cause);
  }

  @Test
  void encryption_exception_is_transient_preserving_cause() {
    var cause = new RuntimeException("provider down");
    assertThat(new EncryptionException("unable to encrypt", cause))
        .isInstanceOf(TransientCodecException.class)
        .isNotInstanceOf(InvalidPayloadException.class)
        .hasCause(cause);
  }
}
```

Imports: `org.jwcarman.codec.spi.InvalidPayloadException`, `org.jwcarman.codec.spi.TransientCodecException`, `org.jwcarman.codec.spi.UnsupportedFormatException`.

`EnvelopeCodecDecodeTest` — add a nested class (the file's `provider()` helper builds a `JceDataKeyProvider` over a 32-byte KEK of `7`s):

```java
  @Nested
  class Unsupported_format {

    @Test
    void an_unknown_format_version_is_unsupported_not_invalid() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] message = codec.encode("x".getBytes(UTF_8));
      message[2] = 0x02; // a version this build does not know

      assertThatExceptionOfType(UnsupportedFormatException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessage("unknown format version: 2")
          .isNotInstanceOf(DecryptionException.class);
    }

    @Test
    void an_unknown_algorithm_id_is_unsupported_not_invalid() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] message = codec.encode("x".getBytes(UTF_8));
      message[3] = 0x02; // the id spec 006 reserves for a future key-committing suite

      assertThatExceptionOfType(UnsupportedFormatException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessage("unknown algorithm id: 2")
          .isNotInstanceOf(DecryptionException.class);
    }

    @Test
    void the_version_is_checked_before_the_algorithm() {
      EnvelopeCodec codec = EnvelopeCodec.builder(provider()).build();
      byte[] message = codec.encode("x".getBytes(UTF_8));
      message[2] = 0x02;
      message[3] = 0x02;

      assertThatExceptionOfType(UnsupportedFormatException.class)
          .isThrownBy(() -> codec.decode(message))
          .withMessageContaining("version");
    }
  }
```

Import `org.jwcarman.codec.spi.UnsupportedFormatException`.

Fuzz tests — in `EnvelopeCodecDecodeFuzzTest` replace `} catch (DecryptionException _) {` with `} catch (InvalidPayloadException | UnsupportedFormatException | TransientCodecException _) {` and update the method's comment to "documented outcomes: spec 006 §2.2 as amended by spec 008"; same catch clause in `EnvelopeCodecMutationFuzzTest`. Add the three `spi` imports to each; `DecryptionException` may become unused there — remove the import if so.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -B -pl codec-crypto -am test`
Expected: FAIL — taxonomy assertions and the two unsupported-format tests (which currently get `DecryptionException`).

- [ ] **Step 3: Re-parent the three exceptions and retype the two throws**

`DecryptionException.java` — `extends IllegalArgumentException` → `extends InvalidPayloadException`; add the import. In its class Javadoc, the opening sentence (currently describing tampering/invalid data) gains: "It is an {@link InvalidPayloadException}: the caller's response is to quarantine, never to retry the same bytes." Keep the indistinguishability paragraph as is.

`KeyAccessException.java` — `extends IllegalStateException` → `extends TransientCodecException`; add the import; class Javadoc gains: "It is a {@link TransientCodecException}: retry, or alert on key infrastructure; never quarantine the data."

`EncryptionException.java` — `extends IllegalStateException` → `extends TransientCodecException`; add the import; class Javadoc gains: "It is a {@link TransientCodecException}: the value is not at fault, the provider or strategy is."

`EnvelopeCodec.java` — lines 188–193 become:

```java
    if (bytes[2] != FORMAT_VERSION) {
      // Well-formed framing from a writer this build does not know: hold it, do not quarantine it.
      throw new UnsupportedFormatException("unknown format version: " + bytes[2]);
    }
    if (bytes[3] != ALGORITHM_AES_256_GCM) {
      throw new UnsupportedFormatException("unknown algorithm id: " + bytes[3]);
    }
```

Add `import org.jwcarman.codec.spi.UnsupportedFormatException;`. In `EnvelopeCodec`'s class Javadoc, wherever the decode failure list names `DecryptionException` for "unknown version/algorithm", say `UnsupportedFormatException` instead.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -B -pl codec-crypto -am test`
Expected: PASS.

- [ ] **Step 5: Amend specs 005 and 006**

`docs/superpowers/specs/005-codec-crypto.md`, §Error handling — after "All three live in `org.jwcarman.codec.crypto`.", add:

```markdown
Amended 2026-09-06 by spec 008: the three classes keep their names and messages
and slot into the SPI's families — `DecryptionException extends
InvalidPayloadException`, `KeyAccessException` and `EncryptionException extend
TransientCodecException`. An unknown format version or algorithm id is no
longer a `DecryptionException`: it is the base `UnsupportedFormatException`,
because a payload from a newer writer is fine data this build cannot read, and
a quarantine policy must not take it. The disallowed-keyId rejection stays a
`DecryptionException` on purpose: it is a security admission decision, and
"route it to a reader that would accept it" is what an attacker steering keyIds
wants.
```

`docs/superpowers/specs/006-codec-crypto-assurance.md`, the decode invariant at the line beginning "Targets: (a) `decode(byte[])` may only throw" — change the list to "`InvalidPayloadException`, `UnsupportedFormatException` or `TransientCodecException` (each possibly via its crypto subclass; amended 2026-09-06 by spec 008)".

- [ ] **Step 6: Full verification (PIT included), format, commit**

Run: `./mvnw -Pci -B clean verify` — BUILD SUCCESS. Check the PIT line in the output: `Generated N mutations Killed N (100%)`. If a mutant survives, it will be on a line this task touched; kill it with a test rather than lowering anything.

```bash
./mvnw -q spotless:apply
git add codec-crypto docs/superpowers/specs/005-codec-crypto.md docs/superpowers/specs/006-codec-crypto-assurance.md
git commit -m "codec-crypto: slot the exception taxonomy into the codec families

DecryptionException is an InvalidPayloadException; KeyAccessException and
EncryptionException are TransientCodecExceptions. An unknown format version
or algorithm id is now UnsupportedFormatException rather than a rejection —
a newer writer's output is fine data this build cannot read. The fuzz
targets' property follows spec 006 as amended. Spec 008."
```

---

### Task 7: The adapters — `codec-kafka` and `codec-spring-data-redis`

**Files:**
- Test: `codec-kafka/src/test/java/org/jwcarman/codec/kafka/CodecKafkaAdaptersTest.java` (add one test in `Deserializer`)
- Test: `codec-spring-data-redis/src/test/java/org/jwcarman/codec/redis/CodecRedisSerializerTest.java` (add one test beside `codec_exceptions_pass_through_unchanged`)

**Interfaces:**
- Consumes: `InvalidPayloadException(String)` from Task 1.
- Produces: nothing — the adapters' main code does not change; these tests pin spec 008's boundary 3 (adapters propagate `CodecException` unchanged).

The existing `codec_exceptions_pass_through_unchanged` tests exercise an `IllegalArgumentException` from an `xmap` function, which is boundary 2 (composition passes through). Keep them; add the family case beside them.

- [ ] **Step 1: Write the tests (they pass immediately — the adapters already propagate)**

`CodecKafkaAdaptersTest`, inside `class Deserializer`:

```java
    @Test
    void a_codec_exception_passes_through_unchanged() {
      InvalidPayloadException rejection = new InvalidPayloadException("nope");
      Codec<String> rejecting =
          new Codec<>() {
            @Override
            public byte[] encode(String value) {
              return new byte[0];
            }

            @Override
            public String decode(byte[] bytes) {
              throw rejection;
            }
          };
      CodecDeserializer<String> deserializer = new CodecDeserializer<>(rejecting);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> deserializer.deserialize("t", new byte[] {1}))
          .isSameAs(rejection);
    }
```

`CodecRedisSerializerTest`, beside `codec_exceptions_pass_through_unchanged`:

```java
    @Test
    void a_codec_exception_passes_through_unchanged() {
      InvalidPayloadException rejection = new InvalidPayloadException("nope");
      Codec<String> rejecting =
          new Codec<>() {
            @Override
            public byte[] encode(String value) {
              return new byte[0];
            }

            @Override
            public String decode(byte[] bytes) {
              throw rejection;
            }
          };
      CodecRedisSerializer<String> serializer = CodecRedisSerializer.of(rejecting);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> serializer.deserialize(new byte[] {1}))
          .isSameAs(rejection);
    }
```

Imports in both: `static org.assertj.core.api.Assertions.assertThatExceptionOfType`, `org.jwcarman.codec.spi.InvalidPayloadException`.

- [ ] **Step 2: Run them**

Run: `./mvnw -B -pl codec-kafka,codec-spring-data-redis -am test`
Expected: PASS. (These are regression pins, not TDD reds; that is deliberate — the behaviour already exists and the spec now depends on it.)

- [ ] **Step 3: Full verification, format, commit**

Run: `./mvnw -Pci -B clean verify` — BUILD SUCCESS.

```bash
./mvnw -q spotless:apply
git add codec-kafka codec-spring-data-redis
git commit -m "Adapters: pin that codec exceptions pass through unchanged

Kafka wraps deserializer failures in RecordDeserializationException itself
and Spring's cache layer handles RuntimeException, so the adapters let the
families through as they are. Spec 008, boundary 3."
```

---

### Task 8: Documentation, CHANGELOG, and the spec 001 amendment

**Files:**
- Create: `docs/guides/error-handling.md`
- Modify: `mkdocs.yml` (nav: add the page after "Codec Composition")
- Modify: `docs/guides/composition.md` (the `UnknownVersionException` paragraph, lines 252–256)
- Modify: `docs/guides/getting-started.md` (lines 47–51: the JSON-B caveat)
- Modify: `docs/guides/encryption.md` (§Error taxonomy, lines 297–326; the `[scheme:1][payload]` sentence, line 24)
- Modify: `docs/guides/threat-model.md` (line 92, the fuzz claim)
- Modify: `docs/guides/fory.md` (one sentence, if it names `ClassCastException`)
- Modify: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/JceDataKeyProvider.java` (Javadoc line 39: `[scheme:1][payload]`)
- Modify: `docs/superpowers/specs/005-codec-crypto.md` (line 157: `[scheme:1][payload]`)
- Modify: `docs/superpowers/specs/001-codec-core.md` (amendment note under §SPI interfaces)
- Modify: `CHANGELOG.md` (a `### Breaking changes` section under `## [Unreleased]`)

**Interfaces:**
- Consumes: the family names and every mapping decision from Tasks 1–7; every snippet below must compile against `codec-core` as committed in Task 1.
- Produces: nothing code depends on.

- [ ] **Step 1: Write the guide page**

Create `docs/guides/error-handling.md`:

````markdown
# Handling failures

Every failure a codec reports from `encode` or `decode` is a `CodecException`,
and more precisely one of four families. The families are not organised by
*what went wrong* or by *which method you called* — you already know which
method you called. They are organised by **what you do next**, so that each
decision a consumer can make is one `catch` clause:

```java
try {
    return codec.decode(bytes);
} catch (InvalidPayloadException e) {
    // The bytes are bad — corrupt, truncated, tampered with, or not ours.
    // Quarantine: dead-letter the record, treat the cache entry as a miss.
} catch (UnsupportedFormatException e) {
    // The bytes are fine; this reader is too old for them. Hold the record,
    // route it to a newer reader, or upgrade. Never quarantine.
} catch (TransientCodecException e) {
    // Something the codec depends on failed — a key service, a JCE provider.
    // The input is not at fault. Retry with backoff, or alert on infrastructure.
}
```

On the encode side there is one more:

```java
try {
    return codec.encode(value);
} catch (InvalidValueException e) {
    // This value cannot be encoded by this codec: a type the backend has no
    // serializer for, a cyclic graph, an unregistered class. Fix the value or
    // the configuration; retrying will not help.
} catch (TransientCodecException e) {
    // As above.
}
```

`catch (CodecException e)` is the coarse form — "something went wrong in the
codec" — for callers that do not distinguish.

## The four families

| Family | Meaning | Your response |
|---|---|---|
| `InvalidValueException` | the value handed to `encode` cannot be encoded | fix the value or the configuration |
| `InvalidPayloadException` | the payload handed to `decode` is malformed, corrupt, or forged | quarantine |
| `UnsupportedFormatException` | the payload is well-formed but this reader cannot handle it | hold, route, upgrade |
| `TransientCodecException` | something the codec depends on failed; the input is not at fault | retry, or alert |

All four are unchecked, all extend only `CodecException` and `RuntimeException`,
and all preserve the underlying library's exception as the cause — the Jackson,
Gson, Fory or JCE detail is one `getCause()` away, but you never have to import
a library type to handle a codec failure.

Some modules add a more specific subclass where it carries information the
family does not: `codec-versioned`'s `UnknownVersionException` (an
`UnsupportedFormatException` that carries the version), and `codec-crypto`'s
`DecryptionException` (an `InvalidPayloadException` whose message is
deliberately uniform across cryptographic rejections), `KeyAccessException` and
`EncryptionException` (both `TransientCodecException`). You catch the family; the
subclass is there when you want to know more.

## Why the third family matters

`InvalidPayloadException` and `UnsupportedFormatException` look similar — in
both cases `decode` refused the bytes — but they demand opposite responses.
During a rolling upgrade, instances that have already been upgraded write
payloads the old instances cannot read. If those were reported as *invalid*, a
dead-letter policy would discard every one of them. They are not invalid; they
are ahead of you. That is why `codec-versioned` reports an unregistered version
as `UnsupportedFormatException`, why `codec-crypto` reports an unknown format
version or algorithm id the same way, and why the two families share no parent
below `CodecException`.

## What is not a codec failure

Construction and configuration errors are programmer errors at wiring time, not
runtime outcomes a pipeline reacts to, and they keep the JDK's types: a bad
builder argument is `IllegalArgumentException`, an inconsistent builder
`IllegalStateException`, a `null` anywhere — including `encode(null)` and
`decode(null)` on a bare codec — `NullPointerException`. `CodecFactory.create`
failing for a type it cannot handle is likewise `IllegalArgumentException`.

Composition passes failures through: `andThen`, `nullSafe` and `xmap` propagate
whatever the underlying codec throws. The functions you hand to `xmap` may throw
anything; that is yours.

The adapters propagate too. `codec-kafka`'s deserializer and
`codec-spring-data-redis`'s serializer let `CodecException` through unchanged;
Kafka itself wraps any deserializer failure in `RecordDeserializationException`,
and Spring's cache layer handles `RuntimeException`.

## Adding to the hierarchy

One rule: **subclass by what the caller does next.** A new failure whose answer
is one of the four above is a subclass of that family, never a fifth sibling; a
more specific subclass earns its existence only when it carries information the
family does not.
````

- [ ] **Step 2: Wire it into the site and cross-reference it**

`mkdocs.yml` — in `nav:` → `Guides:`, add `- Handling Failures: guides/error-handling.md` directly after `- Codec Composition: guides/composition.md`.

`docs/guides/composition.md` — replace the sentence run at lines 252–256:

```markdown
handed data it cannot read. Data written by a version the reader does not know
raises `UnknownVersionException`, which carries the offending version — during a
rollout that means "written by a newer deploy", a condition you may want to
route or retry rather than treat as corruption. A buffer that is not versioned
at all raises the parent `VersionedFormatException`.
```

with:

```markdown
handed data it cannot read. Data written by a version the reader does not know
raises `UnknownVersionException` — an `UnsupportedFormatException` carrying the
offending version — which during a rollout means "written by a newer deploy":
hold or route it, never quarantine it. A buffer that is not versioned at all
raises `VersionedFormatException`, an `InvalidPayloadException`. The two share
no parent below `CodecException`; see [Handling failures](error-handling.md).
```

`docs/guides/getting-started.md` — replace lines 47–51:

```markdown
`codec-jsonb` depends on the Jakarta JSON Binding API only; bring a provider.
Codec is tested against [Eclipse Yasson](https://github.com/eclipse-ee4j/yasson),
the reference implementation. Apache Johnzon also works, with one difference
worth knowing: it surfaces malformed input as JSON-P's `JsonParsingException`
rather than wrapping it in `JsonbException` as the spec describes.
```

with:

```markdown
`codec-jsonb` depends on the Jakarta JSON Binding API only; bring a provider.
Codec is tested against [Eclipse Yasson](https://github.com/eclipse-ee4j/yasson),
the reference implementation; Apache Johnzon also works. Either way, malformed
input surfaces as `InvalidPayloadException` with the provider's own exception
as the cause — see [Handling failures](error-handling.md).
```

`docs/guides/fory.md` — if any sentence says a wrong-type decode is a `ClassCastException`, change it to `InvalidPayloadException`; if none does, no change.

- [ ] **Step 3: Rewrite the crypto error taxonomy and the "payload" wording**

`docs/guides/encryption.md` — replace the `## Error taxonomy` section (from the heading to just before `## Assurance`) with:

```markdown
## Error taxonomy

`codec-crypto` throws three exceptions of its own, all in
`org.jwcarman.codec.crypto`, and never logs. Each slots into one of the SPI's
[families](error-handling.md), which is what you catch:

- **`DecryptionException`** — an `InvalidPayloadException`: "this data is bad."
  Covers bad magic, bounds violations, a disallowed keyId, and cryptographic
  rejection (GCM tag mismatch, or a provider affirmatively rejecting the wrapped
  DEK). Cryptographic rejections all share the exact message
  `"Unable to decrypt data"`, deliberately indistinguishable from each other;
  the exception's *cause* is preserved for diagnosis and does differ by stage.
- **`UnsupportedFormatException`** (the SPI's own, not a crypto subclass) — the
  envelope names a format version or algorithm id this build does not know. A
  newer writer produced it; hold it or route it, do not quarantine it.
- **`KeyAccessException`** — a `TransientCodecException`: "the key
  infrastructure is unavailable, or misbehaving": timeouts, throttling,
  credential expiry, a provider whose `unwrap` returns a key that violates its
  contract, a key the JCE provider cannot use. The cause is preserved.
- **`EncryptionException`** — a `TransientCodecException`: a provider or
  strategy failure during `encode`, wrapping the cause.

!!! danger "Never quarantine or discard data on a TransientCodecException"
    `KeyAccessException` means the KMS was unreachable or the key was unusable,
    not that the data is invalid. A pipeline that quarantines or discards
    ciphertext on decryption failure must catch `InvalidPayloadException`
    (which `DecryptionException` is) and nothing wider — conflating the families
    turns a transient KMS outage into permanent data loss. Retain the encrypted
    data and retry.

This distinction is normative on `DataKeyProvider`: `unwrap` MUST throw
`DecryptionException` only when the KMS or JCE layer has affirmatively
rejected the blob as invalid; every other failure — including plain
availability failures — must propagate as an ordinary runtime exception, which
`EnvelopeCodec` wraps as `KeyAccessException`.
```

Also in `encryption.md`, line 24: `` returns a wrapped blob laid out as `[scheme:1][payload]` `` → `` returns a wrapped blob laid out as `[scheme:1][wrapped key]` ``, and in the same sentence "followed by the 40-byte AES-KW payload" → "followed by the 40-byte AES-KW output". In `JceDataKeyProvider.java` line 39, `{@code [scheme:1][payload]}` → `{@code [scheme:1][wrapped key]}` and "followed by the AES-KW payload" → "followed by the AES-KW output". In spec 005 line 157, the same `[scheme:1][payload]` → `[scheme:1][wrapped key]`. ("Payload" is now the house word for what a codec consumes and produces; the wrapped-key blob is not that.)

`docs/guides/threat-model.md` line 92 — "asserts that `decode` only ever throws `DecryptionException` (the only legitimate outcome from the in-process `JceDataKeyProvider`)" → "asserts that `decode` only ever throws `InvalidPayloadException`, `UnsupportedFormatException` or `TransientCodecException` — the three outcomes spec 006 permits".

- [ ] **Step 4: Amend spec 001 and write the CHANGELOG entry**

`docs/superpowers/specs/001-codec-core.md` — at the end of `### SPI interfaces`, add:

```markdown
Amended 2026-09-06 by spec 008: `codec-core` also ships the failure contract
for `encode` and `decode` — `CodecException` and its four families
(`InvalidValueException`, `InvalidPayloadException`,
`UnsupportedFormatException`, `TransientCodecException`), documented as
`@throws` on the two methods.
```

`CHANGELOG.md` — under `## [Unreleased]`, insert a `### Breaking changes` section **before** the existing `### Changed`:

```markdown
### Breaking changes
- Every failure `Codec.encode` and `Codec.decode` report is now a
  `CodecException` from `codec-core`, in one of four families keyed to what the
  caller does next: `InvalidValueException` (fix the value),
  `InvalidPayloadException` (quarantine), `UnsupportedFormatException` (hold for
  a newer reader), `TransientCodecException` (retry). Anything catching
  `IllegalArgumentException`, `IllegalStateException`, `UncheckedIOException`,
  `ClassCastException`, or a Jackson/Gson/JSON-B/Fory/protobuf exception from
  `encode`/`decode` must catch the family (or `CodecException`) instead; the
  library's exception is still there as the cause. Construction-time exceptions
  are unchanged. `codec-crypto`'s `DecryptionException`, `KeyAccessException`
  and `EncryptionException` keep their names and messages and now extend
  `InvalidPayloadException`, `TransientCodecException` and
  `TransientCodecException` respectively; an unknown envelope version or
  algorithm id is now `UnsupportedFormatException`, not a rejection.
  `codec-versioned`'s `UnknownVersionException` now extends
  `UnsupportedFormatException` and is no longer a `VersionedFormatException`.
  See the new [Handling failures](https://jwcarman.github.io/codec/guides/error-handling/)
  guide.
```

- [ ] **Step 5: Verify the site and the build**

Run: `./mvnw -Pci -B clean verify` — BUILD SUCCESS (the Javadoc change in `JceDataKeyProvider` is compiled).
Run: `mkdocs build --strict` if `mkdocs` is on the PATH (it may not be; the docs CI job runs it either way) — no warnings.

Re-read every snippet you added against the committed classes: `InvalidValueException`, `InvalidPayloadException`, `UnsupportedFormatException`, `TransientCodecException`, `CodecException`, `UnknownVersionException`, `VersionedFormatException`, `DecryptionException`, `KeyAccessException`, `EncryptionException` — names and superclasses exactly as Tasks 1, 5 and 6 committed them.

- [ ] **Step 6: Commit**

```bash
./mvnw -q spotless:apply
git add docs mkdocs.yml CHANGELOG.md codec-crypto/src/main/java/org/jwcarman/codec/crypto/JceDataKeyProvider.java
git commit -m "Document the exception families

A Handling Failures guide with the four catch blocks and the one rule for
extending the hierarchy; the crypto error taxonomy restated in terms of the
families; the getting-started JSON-B caveat retired; 'payload' reserved for
what a codec consumes and produces; spec 001 amended; CHANGELOG breaking
change. Spec 008."
```

---

## Notes for the executor

- Tasks 2–7 are independent of one another once Task 1 is in; they touch disjoint modules. Run them in order anyway — the full `-Pci` build at the end of each is the reactor-wide regression check.
- The one place the spec's mapping and the code's reality diverged is Fory (Task 4): Fory reports malformed input as three unrelated exception types. The plan rules on it explicitly; do not widen the catch further (never `RuntimeException`).
- `StringCodec.encode` stays as it is (Task 2); the spec's hedge is resolved as "no change".
- No adapter main code changes (Task 7); only tests.
- If PIT on `codec-crypto` reports a survivor after Task 6, it is on a line that task touched; add a test, do not touch the threshold.
