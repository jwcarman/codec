# Spec 007 — codec-versioned: format versioning and strategy migration

Date: 2026-09-06
Status: approved scope

## Purpose

Let an application change its storage strategy without a flag day. Today a
`Codec<T>` produces bare bytes: a blob written by Jackson JSON is
indistinguishable from one written by Fory, so switching backends — or adding
compression, or changing compression — means every stored payload becomes
unreadable the moment the new code deploys.

This module writes a small self-describing header ahead of the payload naming
which codec produced it, and dispatches on that header when reading. The
approach is the one `Externalizable` implementations use by hand: version
first, then the body, and the reader branches.

Two properties fall out that matter more than the mechanism:

- **Old data stays readable.** Register the old codec at its version and keep
  it registered; nothing needs rewriting.
- **Rollouts are two-phase.** Deploy the readers for the new version first, let
  them reach every instance, then flip the writers in a second deploy. Data
  written by an upgraded instance is never handed to an instance that cannot
  read it.

Not a core concern: applications that just want JSON bytes should not pay three
bytes and a dispatch for a migration story they do not need. This ships as its
own module and `codec-core` is untouched.

## Module

- Artifact `org.jwcarman.codec:codec-versioned`, package
  `org.jwcarman.codec.versioned`.
- Depends only on `codec-core`. No third-party dependencies, matching
  `codec-transforms`.
- Registered in the parent `pom.xml` module list and in `codec-bom`.

## Wire format

```
[magic:2][version:1][payload:n]

  magic    0xC0 0xDC, fixed
  version  unsigned, 1..255
  payload  the bytes produced by the codec registered at that version
```

Three bytes of overhead. Fixed-width — no varint, no length prefix; the payload
runs to the end of the buffer.

Design notes, for the record:

- **Version `0x00` is reserved and never valid.** An all-zeros buffer must not
  parse as a well-formed frame.
- **255 versions is the cap and that is deliberate.** A store that exhausts 255
  format generations has a problem this module cannot solve. Spending a second
  byte to raise the ceiling costs every payload forever.
- **The magic is a misconfiguration guard, not a legacy discriminator.** This
  design has no unframed legacy data to distinguish (decided 2026-09-06). The
  magic earns its two bytes by turning "this codec was pointed at a store it did
  not write" into a clean error instead of a spurious version dispatch and a
  garbage decode — precisely the failure mode of the migrations this module
  exists to serve. It also keeps the project telling one story about framing:
  `EnvelopeCodec` already leads with magic and a version byte.
- **`0xC0 0xDC` is chosen to collide with nothing in practice.** `0xC0` is an
  invalid UTF-8 lead byte, so no text payload begins with it; it is not gzip
  (`1F 8B`), zlib (`78 ..`), a protobuf field tag (small values), Base64/Base32
  (ASCII), or `{`/`[`.

## API

One public type plus a builder and two exceptions.

### VersionedCodec

```java
Codec<Person> codec = VersionedCodec.<Person>builder()
        .version(1, jacksonFactory.create(Person.class))
        .version(2, foryFactory.create(Person.class).andThen(new ZstdCodec()))
        .writing(2)
        .build();
```

- `static <T> Builder<T> builder()` — the only entry point. `VersionedCodec`
  itself is not otherwise instantiable.
- `Builder.version(int version, Codec<T> codec)` — registers a codec for
  reading. Rejects a version outside `1..255` (`IllegalArgumentException`) and
  rejects re-registering a version already taken (`IllegalStateException`).
  A null codec is a `NullPointerException`.
- `Builder.writing(int version)` — required; names the version `encode` writes.
- `Builder.build()` — returns `Codec<T>`. Throws `IllegalStateException` if
  `writing` was never called, or names a version with no registered codec, or
  if no versions were registered at all.

`writing` is explicit rather than "highest registered wins" because that is the
entire two-phase rollout mechanism. If adding a version silently promoted it to
the write version, the first upgraded instance would immediately begin writing
data its peers cannot read.

The result is a plain `Codec<T>`, so it composes with `andThen`, `xmap`, and
`nullSafe` like any other codec, and needs no adapter to sit in a chain.

### Behavior

`encode(T value)`:

1. Delegate to the codec registered at the write version.
2. Return `[magic][writeVersion][payload]`.

`encode(null)` throws `NullPointerException`, matching the transforms.
`nullSafe()` remains the opt-in for pass-through.

`decode(byte[] bytes)`:

1. Fewer than 3 bytes, or magic mismatch → `VersionedFormatException`.
2. Version byte not registered (including `0x00`) → `UnknownVersionException`
   carrying the version.
3. Otherwise delegate the remainder to that version's codec.

### Errors

- `VersionedFormatException extends RuntimeException` — the base; thrown
  directly for a short buffer or a magic mismatch.
- `UnknownVersionException extends VersionedFormatException` — magic is valid
  but the version is not registered. Exposes `int version()`.

The distinction is operational, not cosmetic: during a rollout,
`UnknownVersionException` means "written by a newer deploy" — a condition a
caller may want to route or retry rather than treat as corruption — while the
base type means the bytes were never ours.

Exceptions thrown by a delegate codec propagate unchanged.

## Versioning transforms

Because the builder is generic over `Codec<T>`, `T = byte[]` gives versioned
byte transforms with no extra machinery: a compression or key-wrapping layer
that can be swapped under an unchanged value codec.

```java
Codec<byte[]> compression = VersionedCodec.<byte[]>builder()
        .version(1, new GzipCodec())
        .version(2, new ZstdCodec())
        .writing(2)
        .build();

Codec<Person> codec = factory.create(Person.class).andThen(compression);
```

## Ordering and composition (documentation requirement)

Everything layered *outside* a versioned codec is frozen for the life of the
store, because it must be undone before the version header can be read.

```java
// header outermost — backend, transforms, everything inside may differ per version
Codec<Person> versioned = VersionedCodec.<Person>builder()
        .version(1, jackson.create(Person.class).andThen(new GzipCodec()))
        .version(2, fory.create(Person.class).andThen(new ZstdCodec()))
        .writing(2)
        .build();

// header now inside gzip — gzip must be undone to read it, so gzip can never change
Codec<Person> frozen = versioned.andThen(new GzipCodec());
```

The second form is legitimate when the outer layer carries its own versioning —
`EnvelopeCodec` does — but it must be a deliberate choice. The rule to state in
both the module Javadoc and the composition guide: **put whatever you might want
to change inside the versioned codec; put only permanent commitments outside
it.**

## Thread-safety

The built codec is immutable — a fixed dispatch table and a write version — and
is therefore thread-safe whenever its delegates are, which the `Codec` contract
already requires. The builder is not thread-safe and is not intended to be
shared.

## Implementation notes

- Dispatch is a `Map<Integer, Codec<T>>`, built once by the builder and frozen
  with `Map.copyOf`. A `Codec<T>[]` of length 256 was considered, but a generic
  array of that shape cannot be created without an unchecked cast, and this
  project forbids suppressing warnings of any kind — so the array is not an
  option here. The lookup boxes an `int` key: versions up to 127 come from
  `Integer`'s cache, and 128-255 allocate a short-lived box. Against a delegate
  that has just serialized an object graph, that is not a measurable cost.
- Encode and decode each cost one array copy. That is inherent to a
  `byte[]`-in/`byte[]`-out SPI and is not worth contorting the API to avoid.

## Testing

- Round trip through each registered version.
- Cross-version read: bytes written by a codec configured `.writing(1)` decode
  correctly through a codec configured `.writing(2)` with both registered — the
  two-phase rollout, asserted.
- Emitted frame is byte-exact: magic, version byte, delegate payload.
- `UnknownVersionException` for a valid frame at an unregistered version, and
  for version `0x00`; asserts `version()` reports the offending value.
- `VersionedFormatException` for an empty buffer, a 1- and 2-byte buffer, and a
  3+ byte buffer with wrong magic.
- A zero-length payload after a valid header reaches the delegate.
- Builder validation: version `0`, version `256`, negative version, duplicate
  registration, missing `writing`, `writing` naming an unregistered version, no
  versions registered, null codec.
- `T = byte[]` transform case round-trips inside a larger chain.
- `encode(null)` throws; `nullSafe()` wrapping passes null through both ways.
- Delegate exceptions propagate unchanged.

## Out of scope

- **A legacy/unframed fallback.** There is no unframed data to read (decided
  2026-09-06). Worth recording that this stays available: a fallback is purely a
  decode-side policy — "magic did not match, hand the whole blob to codec X" —
  and could be added later without touching the wire format. It is a one-way
  door in the other direction, though: once registered, every byte sequence
  decodes as *something*, and genuinely corrupt data reaches the fallback codec
  instead of failing at the frame.
- Spring Boot auto-configuration. Which versions exist and which one writes is
  application knowledge; there is nothing to detect from the classpath.
- A `CodecFactory` decorator. The per-type registration this needs is explicit
  by nature.
- Migration tooling (bulk re-encode, lazy rewrite-on-read). A store-specific
  concern, not a codec one.

## Definition of done

- `codec-versioned` module builds under `mvn clean verify`, registered in the
  parent POM and `codec-bom`.
- Full Javadoc including the ordering rule and a worked migration example.
- Tests above pass; coverage consistent with the other modules' gate.
- README: module table row, and a mention in the composition section.
- `docs/guides/composition.md`: a section on versioning and the ordering rule.
- CHANGELOG entry.
