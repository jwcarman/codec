# Compiler Warnings as Errors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every javac lint category the build can honestly enforce a build error, reduce the codebase's unchecked casts to the single one at `TypeRef`'s type-token bridge, and state in the project rule exactly what is enforced — per spec 009.

**Architecture:** `TypeRef` gains `rawClass()`, the one unchecked cast, documented as such (Task 1). `GsonCodec` drops `TypeToken` for a plain `Type`; `ProtobufCodec` and `ForyCodec` hold `Class<T>` and use the checked `Class.cast` (Task 2). The parent POM's compiler plugin gets `-Xlint:all,-processing,-unchecked -Werror`; whatever that surfaces is fixed at its site — expected: `serialVersionUID` on ten exception classes — and a throwaway probe proves the gate bites (Task 3). CHANGELOG and the proposed `CLAUDE.md` wording close it (Task 4).

**Tech Stack:** Java 25, Maven, JUnit 5 + AssertJ (NO Mockito).

**Spec:** `docs/superpowers/specs/009-compiler-warnings-as-errors.md` — normative. If this plan and the spec disagree, STOP and report.

## Global Constraints

- Verification: `./mvnw -Pci -B clean verify` (plain `verify` skips the gates). While iterating: `./mvnw -B -pl <module> -am test`.
- **No `@SuppressWarnings`, no suppression of any kind** — this plan exists to make that enforceable. If a warning cannot be fixed at its site, STOP and report; do not annotate, do not add a lint exclusion.
- No star imports. Apache 2.0 header on every new file (there are none planned). Javadoc on every public element.
- Behaviour is unchanged by Tasks 1–2: every existing test in `codec-core`, `codec-gson`, `codec-protobuf` and `codec-fory` must pass without modification except where a test asserted an implementation detail this plan removes (none are known).
- Messages unchanged: `ForyCodecFactoryTest.a_generic_array_type_is_rejected` asserts `"Unsupported type"`; `TypeRef.rawClass()`'s message must keep that prefix.
- Before committing: `./mvnw -q spotless:apply`. Commit trailers:
  ```
  Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01PELEtnHV8gBVNcGyfXuvrq
  ```
  Never push.
- Test style: `TypeRefTest`, `GsonCodecFactoryTest`, `ProtobufCodecFactoryTest` use camelCase `should*` names without `@Nested`; `ForyCodecFactoryTest` uses `@Nested` + snake_case. Match the file.

---

### Task 1: `TypeRef.rawClass()` — the one unchecked cast

**Files:**
- Modify: `codec-core/src/main/java/org/jwcarman/codec/spi/TypeRef.java`
- Test: `codec-core/src/test/java/org/jwcarman/codec/spi/TypeRefTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `public Class<T> rawClass()` on `TypeRef<T>` — a `Class` returns itself; a `ParameterizedType` returns its raw type; a `GenericArrayType` throws `IllegalArgumentException` whose message starts with `"Unsupported type: "`. Task 2 calls it from three factories.

- [ ] **Step 1: Write the failing tests**

Add to `TypeRefTest` (camelCase, no nesting), after `shouldRejectATypeVariable`:

```java
  @Test
  void rawClassOfAPlainClassIsTheClass() {
    assertThat(TypeRef.of(String.class).rawClass()).isEqualTo(String.class);
    assertThat(new TypeRef<Integer>() {}.rawClass()).isEqualTo(Integer.class);
  }

  @Test
  void rawClassOfAParameterizedTypeIsItsRawType() {
    assertThat(new TypeRef<List<String>>() {}.rawClass()).isEqualTo(List.class);
    assertThat(new TypeRef<Map<String, List<Integer>>>() {}.rawClass()).isEqualTo(Map.class);
  }

  @Test
  void rawClassOfAPrimitiveArrayIsTheArrayClass() {
    assertThat(TypeRef.of(int[].class).rawClass()).isEqualTo(int[].class);
  }

  @Test
  void rawClassOfAGenericArrayTypeIsRejected() {
    TypeRef<List<String>[]> genericArray = new TypeRef<>() {};

    assertThatThrownBy(genericArray::rawClass)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Unsupported type: ")
        .hasMessageContaining("List<java.lang.String>[]");
  }

  @Test
  void rawClassIsUsableAsACheckedCast() {
    // The whole point: a Class<T> lets a backend use Class.cast instead of an unchecked (T) cast.
    Class<List<String>> raw = new TypeRef<List<String>>() {}.rawClass();
    Object value = List.of("a");

    assertThat(raw.cast(value)).containsExactly("a");
    assertThatThrownBy(() -> raw.cast("not a list")).isInstanceOf(ClassCastException.class);
  }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -B -pl codec-core test`
Expected: COMPILATION FAILURE — `rawClass()` does not exist.

- [ ] **Step 3: Implement**

In `TypeRef.java`, add `import java.lang.reflect.GenericArrayType;` and, after `getType()`:

```java
  /**
   * Returns the erased class of the captured type: {@code List.class} for {@code List<String>},
   * the class itself for a non-generic type.
   *
   * <p>This method contains the one unchecked cast in the codebase. It is sound by construction:
   * a {@code TypeRef<T>} captures {@code T} and nothing else, so the erasure of the captured type
   * is the erasure of {@code T}. Backends use the result with {@link Class#cast} — a checked cast
   * — instead of an unchecked {@code (T)} cast of their own; every other cast in the reactor is a
   * checked {@code Class.cast} or none at all, and no new unchecked cast is permitted anywhere
   * else. The build compiles with {@code -Xlint:all,-processing,-unchecked -Werror}: the
   * {@code unchecked} category is off precisely and only because of this method, since Java cannot
   * express the type-token bridge without it and cannot write an unchecked cast without a warning.
   *
   * @return the erased class of {@code T}
   * @throws IllegalArgumentException if the captured type is a generic array type, which has no
   *     single erased class a codec could be created for
   */
  public Class<T> rawClass() {
    Class<?> raw;
    if (type instanceof Class<?> clazz) {
      raw = clazz;
    } else if (type instanceof ParameterizedType parameterized) {
      raw = (Class<?>) parameterized.getRawType();
    } else {
      throw new IllegalArgumentException("Unsupported type: " + type.getTypeName());
    }
    return uncheckedTypeToken(raw);
  }

  /**
   * The type-token bridge: the one place the erased class is asserted to be {@code Class<T>}.
   * Isolated so the unchecked cast has exactly one line to live on.
   */
  private static <T> Class<T> uncheckedTypeToken(Class<?> raw) {
    return (Class<T>) raw;
  }
```

(`GenericArrayType` is the only `Type` the constructor can capture that is neither a `Class` nor a `ParameterizedType`: type variables are rejected at capture, and a bare wildcard cannot be a type argument to an anonymous subclass. The import is for the Javadoc reader's benefit if you reference it; drop it if unused so `-Xlint` does not flag it later.)

- [ ] **Step 4: Run to verify they pass**

Run: `./mvnw -B -pl codec-core test`
Expected: PASS.

- [ ] **Step 5: Full verification, format, commit**

Run: `./mvnw -Pci -B clean verify` — BUILD SUCCESS.

```bash
./mvnw -q spotless:apply
git add codec-core
git commit -m "TypeRef.rawClass(): the one unchecked cast, in one place

The erased class of a captured type, typed Class<T> so backends can use the
checked Class.cast instead of their own unchecked (T) casts. Sound by
construction — a TypeRef<T> captures T and nothing else — and isolated to a
single private method so the cast has exactly one line to live on. Spec 009."
```

---

### Task 2: The three backends off their unchecked casts

**Files:**
- Modify: `codec-gson/src/main/java/org/jwcarman/codec/gson/GsonCodec.java`, `GsonCodecFactory.java`
- Modify: `codec-protobuf/src/main/java/org/jwcarman/codec/protobuf/ProtobufCodec.java`, `ProtobufCodecFactory.java`
- Modify: `codec-fory/src/main/java/org/jwcarman/codec/fory/ForyCodec.java`, `ForyCodecFactory.java`
- Tests: existing suites in all three modules must pass unchanged; one new test per module pins the behaviour the cast used to hide.

**Interfaces:**
- Consumes: `TypeRef.rawClass()` from Task 1.
- Produces: none new. Package-private constructors change: `GsonCodec(Gson, Type)`, `ProtobufCodec(Parser<? extends GeneratedMessage>, Class<T>)`, `ForyCodec(ThreadSafeFory, Class<T>)`.

- [ ] **Step 1: Confirm the three casts are the only ones**

Run: `./mvnw -B -q clean compile 2>&1 | grep "unchecked or unsafe"`
Expected: exactly three lines — `GsonCodecFactory.java`, `ProtobufCodecFactory.java`, `ForyCodec.java`. Record them in the report.

- [ ] **Step 2: Write the failing tests**

`GsonCodecFactoryTest` (camelCase, no nesting) — add:

```java
  @Test
  void shouldRoundTripAParameterizedTypeThroughItsType() {
    Codec<Map<String, List<Integer>>> codec =
        factory.create(new TypeRef<Map<String, List<Integer>>>() {});
    Map<String, List<Integer>> value = Map.of("a", List.of(1, 2));

    assertThat(codec.decode(codec.encode(value))).isEqualTo(value);
  }
```

`ProtobufCodecFactoryTest` (camelCase) — add:

```java
  @Test
  void shouldDecodeToTheRequestedMessageClass() {
    Codec<TestMessages.Person> codec = factory.create(TestMessages.Person.class);
    TestMessages.Person person = TestMessages.Person.newBuilder().setName("Alice").build();

    TestMessages.Person decoded = codec.decode(codec.encode(person));

    assertThat(decoded).isInstanceOf(TestMessages.Person.class).isEqualTo(person);
  }
```

(Use whatever builder fields `TestMessages.Person` actually has — read `codec-protobuf/src/test/proto` or the generated class; the assertion that matters is `isInstanceOf`.)

`ForyCodecFactoryTest`, nested `Failures` — the existing `decoding_a_value_of_the_wrong_type_is_an_invalid_payload` already pins the message; add one that pins the checked cast never leaks a `ClassCastException`:

```java
    @Test
    void a_wrong_type_payload_never_surfaces_as_a_class_cast_exception() {
      byte[] person = factory.create(Person.class).encode(new Person("Alice", 30, true));
      Codec<Order> orders = factory.create(Order.class);

      assertThatExceptionOfType(InvalidPayloadException.class)
          .isThrownBy(() -> orders.decode(person))
          .isNotInstanceOf(ClassCastException.class);
    }
```

- [ ] **Step 3: Run to verify they fail or pass**

Run: `./mvnw -B -pl codec-gson,codec-protobuf,codec-fory -am test`
Expected: the three new tests PASS against the current code — they are regression pins for a refactor that must not change behaviour, not TDD reds. Confirm the suites are green before touching production code, so any later failure is yours.

- [ ] **Step 4: Gson — hold a `Type`, drop `TypeToken`**

`GsonCodec.java`:

```java
class GsonCodec<T> implements Codec<T> {

  private final Gson gson;
  private final Type type;

  GsonCodec(Gson gson, Type type) {
    this.gson = gson;
    this.type = type;
  }

  @Override
  public byte[] encode(T value) {
    try {
      return gson.toJson(value, type).getBytes(StandardCharsets.UTF_8);
    } catch (JsonParseException e) {
      throw new InvalidValueException("Unable to encode value as JSON", e);
    }
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return gson.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
    } catch (JsonParseException e) {
      throw new InvalidPayloadException("Unable to decode JSON", e);
    }
  }
}
```

`Gson.fromJson(String, Type)` is `<T> T fromJson(String, Type)` — generic in its return, so the assignment to `T` needs no cast. Replace `import com.google.gson.reflect.TypeToken;` with `import java.lang.reflect.Type;`.

`GsonCodecFactory.create` becomes:

```java
  @Override
  public <T> Codec<T> create(TypeRef<T> typeRef) {
    Objects.requireNonNull(typeRef, "typeRef must not be null");
    return new GsonCodec<>(gson, typeRef.getType());
  }
```

Remove the `TypeToken` import.

- [ ] **Step 5: Protobuf — `Class<T>` and `Class.cast`**

`ProtobufCodec.java`:

```java
class ProtobufCodec<T> implements Codec<T> {

  private final Parser<? extends GeneratedMessage> parser;
  private final Class<T> type;

  ProtobufCodec(Parser<? extends GeneratedMessage> parser, Class<T> type) {
    this.parser = parser;
    this.type = type;
  }

  @Override
  public byte[] encode(T value) {
    // type was verified to be a GeneratedMessage subclass at creation, so this cast is checked
    // and cannot fail for a T.
    return GeneratedMessage.class.cast(value).toByteArray();
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return type.cast(parser.parseFrom(bytes));
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidPayloadException("Failed to decode protobuf message", e);
    }
  }
}
```

The class no longer declares `T extends GeneratedMessage` — that bound is what forced the unchecked cast in the factory. `ProtobufCodecFactory.create` becomes:

```java
  @Override
  public <T> Codec<T> create(TypeRef<T> typeRef) {
    Type type = typeRef.getType();
    if (!(type instanceof Class<?>)) {
      throw new IllegalArgumentException(
          "Protobuf codecs do not support parameterized types: " + type);
    }
    Class<T> clazz = typeRef.rawClass();
    if (!GeneratedMessage.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException(
          "Type " + clazz.getName() + " is not a GeneratedMessage subclass");
    }
    Parser<? extends GeneratedMessage> parser = getParser(clazz.asSubclass(GeneratedMessage.class));
    return new ProtobufCodec<>(parser, clazz);
  }
```

Both existing `IllegalArgumentException` messages are unchanged (`ProtobufCodecFactoryTest` asserts them).

- [ ] **Step 6: Fory — `Class<T>` and `Class.cast`**

`ForyCodec.java`: change the field and constructor to `Class<T> rawType` / `ForyCodec(ThreadSafeFory fory, Class<T> rawType)`, and the end of `decode` to:

```java
    if (value != null && !rawType.isInstance(value)) {
      throw new InvalidPayloadException(
          "Decoded a " + value.getClass().getName() + " but expected " + rawType.getName());
    }
    return rawType.cast(value);
```

(`Class.cast(null)` returns `null`, preserving the null round-trip test.) `ForyCodecFactory.create` becomes:

```java
  @Override
  public <T> Codec<T> create(TypeRef<T> typeRef) {
    Objects.requireNonNull(typeRef, "typeRef must not be null");
    requireRegistered(typeRef.getType());
    return new ForyCodec<>(fory, typeRef.rawClass());
  }
```

Delete the private `rawClass(Type)` helper (lines ~168–176) — `TypeRef.rawClass()` throws the same `"Unsupported type: …"` message for a generic array, which `a_generic_array_type_is_rejected` asserts. Remove the now-unused `ParameterizedType` import only if `requireRegistered`/`collectUnregistered` no longer use it (they do — keep it).

- [ ] **Step 7: Prove the casts are gone, run everything**

Run: `./mvnw -B -q clean compile 2>&1 | grep -c "unchecked or unsafe"`
Expected: `0`.

Run: `./mvnw -B -pl codec-gson,codec-protobuf,codec-fory -am test` — PASS, every existing test unchanged.

- [ ] **Step 8: Full verification, format, commit**

Run: `./mvnw -Pci -B clean verify` — BUILD SUCCESS (PIT and SpotBugs are on codec-crypto, untouched here).

```bash
./mvnw -q spotless:apply
git add codec-gson codec-protobuf codec-fory
git commit -m "Gson, Protobuf, Fory: no unchecked casts

Gson holds the TypeRef's Type and lets Gson's generic fromJson infer the
return; Protobuf and Fory hold the Class<T> from TypeRef.rawClass() and use
the checked Class.cast. javac's 'uses unchecked or unsafe operations' note
is gone from every module. Behaviour unchanged; the existing suites pass
untouched. Spec 009."
```

---

### Task 3: `-Xlint:all,-processing,-unchecked -Werror`, and everything it surfaces

**Files:**
- Modify: `pom.xml` (`maven-compiler-plugin` in `<pluginManagement>`, ~line 148)
- Modify: the ten exception classes (`serialVersionUID`): `codec-core/.../spi/{CodecException,InvalidValueException,InvalidPayloadException,UnsupportedFormatException,TransientCodecException}.java`, `codec-crypto/.../crypto/{DecryptionException,EncryptionException,KeyAccessException}.java`, `codec-versioned/.../versioned/{VersionedFormatException,UnknownVersionException}.java`
- Modify: whatever else the first `-Werror` build reports — fixed at each site, recorded in the report.

**Interfaces:**
- Consumes: Tasks 1–2 (the `unchecked` note must already be gone; this task does not rely on it, since `unchecked` is excluded, but the report should confirm the codebase is at exactly one unchecked cast).
- Produces: a build that fails on any warning in an enabled category.

- [ ] **Step 1: Turn the flags on**

In `pom.xml`, the `maven-compiler-plugin` entry in `<pluginManagement>` currently has only `<version>`. Add a configuration:

```xml
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>${compiler.plugin.version}</version>
                    <configuration>
                        <showWarnings>true</showWarnings>
                        <compilerArgs>
                            <!-- Every lint category javac has, as errors, except:
                                 - processing: "no processor claimed these annotations" from the JMH
                                   annotation processor in codec-benchmarks; no defect semantics.
                                 - unchecked: deliberately off. The one unchecked cast in the codebase
                                   is TypeRef.rawClass(), the type-token bridge the type system cannot
                                   express, and Java cannot write an unchecked cast without a warning.
                                   Every other cast is a checked Class.cast or none. See spec 009 and
                                   CLAUDE.md. -->
                            <arg>-Xlint:all,-processing,-unchecked</arg>
                            <arg>-Werror</arg>
                        </compilerArgs>
                    </configuration>
                </plugin>
```

- [ ] **Step 2: Build once and collect everything it reports**

Run: `./mvnw -B clean test-compile 2>&1 | grep -E "^\[ERROR\].*\.java|warning:|\[serial\]|\[this-escape\]|\[deprecation\]|\[removal\]|\[cast\]|\[rawtypes\]|\[static\]|\[try\]|\[fallthrough\]|\[dep-ann\]|\[overrides\]|\[varargs\]|\[options\]" | sort | uniq`

Paste the full list into the report before fixing anything. Expected at minimum: ten `[serial]` warnings, one per exception class above. Anything else is real information — record it.

- [ ] **Step 3: Fix `serial` — every exception class**

In each of the ten classes, as the first member of the class body:

```java
  private static final long serialVersionUID = 1L;
```

No Javadoc is required on a private field; do not add `@Serial` (it is fine but unnecessary and the rule is about warnings, not annotations).

- [ ] **Step 4: Fix everything else the list contains, at its site**

Rules, in priority order:
- `deprecation` / `removal`: move to the replacement API. The only sanctioned `@SuppressWarnings("deprecation")` is CLAUDE.md's — a deprecated API the project's *spec* mandates, with a comment naming the spec contract. None is expected; if one appears, STOP and report which API and which spec would justify it rather than adding the annotation.
- `this-escape`: restructure the constructor so `this` does not escape before construction completes (typically: move the call out of the constructor, or make the class/method `final`). Do not weaken to a warning.
- `cast`: remove the redundant cast.
- `rawtypes`: parameterise.
- `static`, `try`, `fallthrough`, `dep-ann`, `overrides`, `varargs`, `options`, `text-blocks`, `divzero`, `empty`, `finally`: fix as the message says.
- Test sources compile under the same flags; a test that warns is fixed the same way.

Re-run the Step 2 command after each batch until it prints nothing.

- [ ] **Step 5: Prove the gate bites (throwaway)**

Create `codec-core/src/main/java/org/jwcarman/codec/spi/Probe.java` containing a class that violates an enabled category — simplest is `public class Probe extends RuntimeException {}` with no `serialVersionUID`. Run `./mvnw -B -pl codec-core clean compile`; expected: **COMPILATION FAILURE** with a `[serial]` error naming `Probe.java`. Delete the file. Record the failing output in the report. If the build passed, the flags are not in effect — STOP and find out why before continuing (the usual cause: the configuration landed under `<plugins>` in a profile rather than `<pluginManagement>`, or a module overrides the plugin).

- [ ] **Step 6: Full verification, format, commit**

Run: `./mvnw -Pci -B clean verify` — BUILD SUCCESS across every module including `codec-benchmarks` (which is where `-processing` matters) and the test sources.

```bash
./mvnw -q spotless:apply
git add pom.xml codec-core codec-crypto codec-versioned   # plus any other module Step 4 touched
git commit -m "Compiler warnings are build errors

javac now runs with -Xlint:all,-processing,-unchecked -Werror across the
reactor. processing is the JMH annotation processor's noise; unchecked is
off for exactly one reason — TypeRef.rawClass() is the type-token bridge
the type system cannot express — and no other unchecked cast exists.
Every category that had been silent is now an error; the ten exception
classes gain a serialVersionUID, and <list whatever else Step 4 fixed>.
A throwaway class without serialVersionUID was confirmed to fail the
build before being removed. Spec 009."
```

---

### Task 4: CHANGELOG and the proposed `CLAUDE.md` paragraph

**Files:**
- Modify: `CHANGELOG.md` (`## [Unreleased]`)
- Modify: `CLAUDE.md` (repository root) — **only if the owner has said to**; otherwise write the paragraph into the report and the commit message says it is proposed.

**Interfaces:** none.

- [ ] **Step 1: CHANGELOG**

Under `## [Unreleased]` → `### Added`, add:

```markdown
- `TypeRef.rawClass()`: the erased class of the captured type, typed `Class<T>`,
  so a backend can use the checked `Class.cast` instead of an unchecked cast of
  its own. It is the single unchecked cast in the codebase and its Javadoc says so
```

Under `### Changed`, add:

```markdown
- The build compiles with `-Xlint:all,-processing,-unchecked -Werror`: any javac
  warning in an enabled category fails compilation. `unchecked` is off because
  `TypeRef.rawClass()` is the one unchecked cast the type system cannot express;
  no other is permitted. The Gson, Protobuf and Fory backends no longer carry
  unchecked casts, and every exception class declares a `serialVersionUID`
```

- [ ] **Step 2: The `CLAUDE.md` paragraph**

The repository `CLAUDE.md`'s "Code Quality" section is the owner's file. Spec 009 proposes this paragraph; apply it only if the owner has explicitly said so in the dispatch, and otherwise put it in the report verbatim for them:

> The build enforces this: javac runs with `-Xlint:all,-processing,-unchecked -Werror`, so any warning in an enabled category fails compilation. The `unchecked` category is off because the codebase contains exactly one unchecked cast — `TypeRef.rawClass()`, the type-token bridge the type system cannot express — and Java cannot write an unchecked cast without a warning. No other unchecked cast is permitted; every other cast is a checked `Class.cast` or none. A change that needs a second unchecked cast is a design problem, not a candidate for an annotation.

- [ ] **Step 3: Verify and commit**

Run: `./mvnw -Pci -B clean verify` — BUILD SUCCESS (nothing compiled changed, but the gate is the gate).

```bash
git add CHANGELOG.md   # and CLAUDE.md only if applied
git commit -m "CHANGELOG: warnings as errors, TypeRef.rawClass()

Spec 009."
```

---

## Notes for the executor

- Task 3, Step 2 is where this plan meets reality: the list of categories `-Xlint:all` surfaces is not knowable until the build runs. Fix at the site; never annotate; if a fix is not possible without a suppression, STOP and report — that is a finding for the owner, not a decision for you.
- The `unchecked` exclusion is not a loophole to widen. If a change you are making seems to need a cast that would warn under `-Xlint:unchecked`, it does not belong in this plan.
- `codec-benchmarks` compiles under the same flags. Its JMH-generated sources live under `target/` and are compiled by the same plugin; if they warn in an enabled category, that is a real finding — report it rather than excluding the module.
