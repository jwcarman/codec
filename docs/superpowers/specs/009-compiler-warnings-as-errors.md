# Spec 009 — Compiler warnings are build errors

Date: 2026-09-06
Status: approved scope

## Purpose

The project rule "never suppress warnings" is stated in `CLAUDE.md` but not
enforced by the build: `maven-compiler-plugin` runs with no `-Xlint` and javac's
default output is a one-line note. Three files carry unchecked casts today and
nothing fails. This spec makes every warning category the build can honestly
enforce a build error, reduces the codebase's unchecked casts to the single one
the type system cannot express, and states in the rule exactly what is and is
not enforced.

## The three unchecked casts today

| Site | Nature | Resolution |
|---|---|---|
| `GsonCodecFactory.create`: `(TypeToken<T>) TypeToken.get(type)` | avoidable | Drop `TypeToken`. `Gson.fromJson(String, Type)` is generic in its return and `toJson(Object, Type)` takes a `Type`; `GsonCodec` holds the `java.lang.reflect.Type` from the `TypeRef` and no cast exists. |
| `ProtobufCodecFactory.create`: `(Codec<T>) codec` | inherent to the type-token bridge | `ProtobufCodec<T>` holds `Class<T>` and `Parser<?>`; `decode` returns `type.cast(parser.parseFrom(bytes))` — a checked cast. |
| `ForyCodec.decode`: `(T) value` after `rawType.isInstance(value)` | inherent to the type-token bridge | `ForyCodec<T>` holds `Class<T>`; `decode` returns `rawType.cast(value)`, which throws `ClassCastException` on a mismatch — caught and reported as `InvalidPayloadException` with the same message as today. |

Both "inherent" fixes need a `Class<T>` from a `TypeRef<T>`. `TypeRef.getType()`
returns a raw `Type`; producing `Class<T>` from it is an unchecked cast, and it
is the one the super-type-token pattern cannot avoid: `T` is known only through
the captured `Type`. Guava's `TypeToken`, Jackson's `TypeReference` and Gson's
own all carry exactly this cast at the same bridge. Java offers no way to write
an unchecked cast without javac warning about it.

## The single bridge: `TypeRef.rawClass()`

`codec-core`'s `TypeRef<T>` gains:

```java
/**
 * The erased class of the captured type: {@code List.class} for {@code List<String>}, the
 * class itself for a non-generic type.
 *
 * <p>This method contains the one unchecked cast in the codebase. It is sound by construction:
 * a {@code TypeRef<T>} captures {@code T} and nothing else, so the erasure of the captured type
 * is the erasure of {@code T}. Every other cast in the reactor is a checked {@link Class#cast}
 * or none at all; no new unchecked cast is permitted anywhere else.
 *
 * @return the erased class of {@code T}
 * @throws IllegalArgumentException if the captured type has no single erased class (a wildcard
 *     or a generic array type)
 */
public Class<T> rawClass()
```

Behaviour: a `Class` returns itself; a `ParameterizedType` returns its raw
type; a `GenericArrayType` or `WildcardType` throws (the SPI has never accepted
those for codec creation, and `TypeRef`'s constructor already rejects type
variables). The `Class<? super T>`-versus-`Class<T>` subtlety for parameterized
types — `List<String>` erases to `List`, which is not literally `Class<List<String>>` —
is exactly what the unchecked cast papers over, and the Javadoc says so.

`ProtobufCodecFactory` and `ForyCodecFactory` use `rawClass()` instead of their
own `Type`-to-`Class` code. `ForyCodecFactory`'s registration walk keeps its own
`Type` inspection; only the codec construction changes.

## Compiler configuration

Parent POM, `maven-compiler-plugin` in `pluginManagement`:

```xml
<configuration>
    <showWarnings>true</showWarnings>
    <compilerArgs>
        <!-- Every lint category javac has, as errors, except:
             - processing: "no processor claimed these annotations" from the JMH
               annotation processor in codec-benchmarks; no defect semantics.
             - unchecked: deliberately off. The one unchecked cast in the codebase is
               TypeRef.rawClass(), the type-token bridge the type system cannot express,
               and Java cannot write an unchecked cast without a warning. Every other
               cast is a checked Class.cast or none. See spec 009 and CLAUDE.md. -->
        <arg>-Xlint:all,-processing,-unchecked</arg>
        <arg>-Werror</arg>
    </compilerArgs>
</configuration>
```

This turns on every category that is silent today. Nothing enforced today
becomes weaker: `unchecked` is already off (javac's default emits only the
summary note), and it stays off for the one reason above.

### What `-Xlint:all -Werror` is expected to surface

Established by the first build, not by this spec; likely:

- `serial` — every exception class without a `serialVersionUID`: the five in
  `codec-core`, three in `codec-crypto`, two in `codec-versioned`. Each gains
  `private static final long serialVersionUID = 1L;`.
- `this-escape` — constructors that leak `this` or call overridable methods;
  builders are the likely site. Fixed by restructuring, never by annotation.
- `deprecation` — any call into a deprecated Fory, Jackson, Gson, JSON-B, Kafka
  or Spring API. Fixed by moving to the replacement API. The only sanctioned
  `@SuppressWarnings("deprecation")` remains the `CLAUDE.md` exception: a
  deprecated API the project's spec mandates, with a comment naming the spec
  contract. None is expected here.
- `cast`, `rawtypes`, `static`, `try`, `fallthrough`, `overrides`, `dep-ann`,
  `divzero`, `empty`, `finally`, `varargs`, `text-blocks`, `removal`, `options`
  — expected clean; each hit is fixed at its site.

`codec-benchmarks` is not published and is already excluded from coverage and
Sonar; it compiles under the same flags regardless (the `-processing` exclusion
is what it needs).

## Rule wording (amends the repository `CLAUDE.md`)

The "Code Quality" section's suppression rule gains one paragraph, proposed
text — the owner edits `CLAUDE.md`, this spec only proposes:

> The build enforces this: javac runs with `-Xlint:all,-processing,-unchecked
> -Werror`, so any warning in an enabled category fails compilation. The
> `unchecked` category is off because the codebase contains exactly one
> unchecked cast — `TypeRef.rawClass()`, the type-token bridge the type system
> cannot express — and Java cannot write an unchecked cast without a warning.
> No other unchecked cast is permitted; every other cast is a checked
> `Class.cast` or none. A change that needs a second unchecked cast is a design
> problem, not a candidate for an annotation.

## Testing

- `TypeRefTest`: `rawClass()` on a plain class, a parameterized type (`List<String>`
  → `List.class`), a nested parameterized type (`Map<String, List<Integer>>` →
  `Map.class`), `of(int[].class)` → `int[].class`; throws
  `IllegalArgumentException` for a wildcard-only capture and a generic array type.
- `GsonCodecFactoryTest`, `ProtobufCodecFactoryTest`, `ForyCodecFactoryTest`:
  every existing round-trip and failure test passes unchanged — the refactors are
  behaviour-preserving. `ForyCodecFactoryTest`'s wrong-type test keeps its message
  assertion (`"Decoded a ... but expected ..."`), so the `Class.cast` path must
  produce the same message as the current `isInstance` check.
- Build: `./mvnw -Pci -B clean verify` green under the new flags across the
  reactor, and a deliberate probe — a scratch class with an unchecked-but-not-
  `unchecked` warning, e.g. a `serial` violation — confirmed to fail compilation
  before being deleted. That probe is executed during implementation and
  recorded in the plan's report, not committed.

## Out of scope

- Enabling `unchecked` via a second mechanism (a separate warnings-only pass,
  an annotation processor, ErrorProne). If the project ever wants "exactly one
  unchecked cast" mechanically enforced, that is a separate decision.
- Any change to test-scope compilation flags beyond what the same plugin
  configuration applies (tests compile under the same `-Werror`; a test that
  warns is fixed like main code).

## Definition of done

- The three casts resolved as tabled; `TypeRef.rawClass()` shipped with Javadoc
  and tests; `-Xlint:all,-processing,-unchecked -Werror` in the parent POM with
  the comment above.
- Every category javac reports on the first build fixed at its site; zero
  `@SuppressWarnings` added.
- `CLAUDE.md` amended by the owner with the proposed paragraph (or their own
  wording).
- CHANGELOG entry under Changed: `TypeRef.rawClass()` added; compiler warnings
  are build errors.
- Sonar's three open issues from the earlier review remain closed and no new
  ones appear on the next scan.
