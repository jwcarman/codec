# Spec 008 — Exception families: one contract for `encode` and `decode` failures

Date: 2026-09-06
Status: approved scope; amends 005 and 007 where noted

## Purpose

`Codec.encode` and `Codec.decode` say nothing about failure, and every module
has answered the question for itself. "The bytes are bad" surfaces today as
`IllegalArgumentException` (protobuf, the text encodings, checksum, crypto),
`UncheckedIOException` (Jackson 2, every compression codec), `IllegalStateException`
(the decompression cap), a bare `RuntimeException` subclass (`codec-versioned`),
the wrapped library's own type (Jackson 3, Gson, JSON-B, Fory), or
`ClassCastException` (Fory, wrong type). A consumer writing policy — a Kafka
deserializer deciding to dead-letter a poison record, a cache deciding an
unreadable entry is a miss, a pipeline deciding whether to retry — has no single
thing to catch and no contract to rely on.

`codec-crypto` already solved this for itself with a deliberate split between
"this data is bad" (`DecryptionException`) and "the infrastructure is
unavailable" (`KeyAccessException`), and two independent reviews this week
singled that taxonomy out as the module's best design decision. This spec lifts
the same idea into the SPI and adds the one family crypto did not need but the
rest of the library does.

## The principle

**Subclass by what the caller does next.** A family exists for each distinct
response a consumer can have to a failure. That is the only rule for adding to
the hierarchy: a new failure that has one of the existing answers is a subclass
of that family, never a sibling. A specialised subclass earns its existence
only when it carries information the family does not (`UnknownVersionException`
carries the version; a hypothetical `GzipDecodeException` would carry nothing).

Direction — encode versus decode — is deliberately *not* an axis. The caller
knows which method they invoked; a hierarchy organised by direction tells the
catch block what it already knows, and splits "infrastructure unavailable" into
two names for one event.

## The hierarchy

All in `org.jwcarman.codec.spi`, in `codec-core`. All unchecked. Every one
carries a message and an optional cause; the wrapped library's exception is
always preserved as the cause.

```
CodecException                 extends RuntimeException
├── InvalidValueException      the VALUE handed to encode cannot be encoded
├── InvalidPayloadException    the PAYLOAD handed to decode is malformed, corrupt, or forged
├── UnsupportedFormatException the payload is well-formed but this reader cannot handle it
└── TransientCodecException    something the codec depends on failed; the input is not at fault
```

| Family | Caller's response | Examples |
|---|---|---|
| `InvalidValueException` | fix the value or the configuration | a type Jackson has no serializer for; a cyclic graph; a Fory class not registered |
| `InvalidPayloadException` | quarantine / dead-letter / treat as miss | bad magic, truncation, tag mismatch, checksum mismatch, invalid Base32, a corrupt gzip stream, a decompression bomb, a payload that decodes to the wrong type |
| `UnsupportedFormatException` | hold, route to a newer reader, or upgrade | a `codec-versioned` version not registered here; a `codec-crypto` format version or algorithm id this build does not know |
| `TransientCodecException` | retry with backoff, or alert on infrastructure | KMS timeout or throttling; a JCE transform the provider cannot supply; a `DataKeyProvider` returning a key that violates its contract |

`CodecException` itself is never thrown directly; it exists for the coarse
`catch (CodecException e)` — "anything went wrong in the codec".

### Naming

- `CodecException` is the only name a consumer *must* reference, and it is the
  obvious one.
- `InvalidValueException` / `InvalidPayloadException` pair off: same shape, one
  per direction, both meaning "your input was the problem". "Value" is the
  SPI's own noun (`encode(T value)`, "the value side" in `xmap`'s Javadoc).
  "Payload" is adopted as the house word for what a codec produces and
  consumes; it is already the majority usage in the docs and the industry norm.
  The narrower sense in `codec-crypto` — the body after the header — is
  replaced with "body" or "ciphertext" wherever it appears in prose (the
  wire-format table already says "ciphertext").
- `UnsupportedFormatException` names the cause precisely: a version or
  algorithm this instance does not know.
- `TransientCodecException` names the response. Precedent: Spring's
  `TransientDataAccessException` under `DataAccessException`. The word slightly
  overclaims for a misconfigured provider, which is not transient — but the
  consumer's response (retry, alert on infrastructure, never blame the input)
  is the same, and that is what the family is for.
- Rejected names, and why: `MalformedInputException` collides with
  `java.nio.charset.MalformedInputException`, which `StringCodec` already
  handles; `CodecUnavailableException` blames the codec for its dependency;
  `BackendUnavailableException` and `ProviderUnavailableException` collide
  with house terms ("backend" = Jackson/Gson/Fory; "provider" = both
  `DataKeyProvider` and JCE `Provider`); `UnencodableValueException` was
  accurate and unlovely.

### JDK roots

The families extend `CodecException` → `RuntimeException` and nothing else.
Compatibility with `catch (IllegalArgumentException)` is deliberately not a
goal (decided 2026-09-06): it would force each family under a JDK type that is
honest for some members and false for others (`UnsupportedFormatException` is
not an illegal argument), and a library that owns its failure vocabulary should
not borrow the JDK's.

## Boundaries

1. **`encode` and `decode` only.** Construction and configuration errors stay
   what they are: `IllegalArgumentException` for a bad builder argument,
   `IllegalStateException` for an inconsistent builder, `NullPointerException`
   for a null argument anywhere — including `encode(null)` and `decode(null)`
   on a bare codec. Those are programmer errors at wiring time, not runtime
   outcomes a pipeline reacts to. `CodecFactory.create(...)` failures
   (unregistered Fory class, unsupported type) likewise remain
   `IllegalArgumentException`.
2. **Composition passes through.** `andThen`, `nullSafe` and `xmap` propagate
   whatever the underlying codec throws, unchanged. The functions a caller
   hands to `xmap` may throw anything; that is theirs, and it passes through
   as documented today.
3. **Adapters propagate.** `codec-kafka` and `codec-spring-data-redis` let
   `CodecException` through unchanged; Kafka wraps any deserializer exception
   in `RecordDeserializationException` itself, and Spring's cache layer
   handles `RuntimeException`. Mapping onto each framework's own exception
   type is a possible follow-up, not part of this spec.

## Mapping

Every existing throw site on an `encode`/`decode` path, and where it lands.
Construction-time throws are not listed; they do not change.

### `codec-crypto` (amends spec 005 §Error handling)

| Today | Becomes |
|---|---|
| `DecryptionException extends IllegalArgumentException` — structural failures with stage messages; cryptographic rejections with the uniform message | `extends InvalidPayloadException`; message policy unchanged |
| `DecryptionException` for **unknown format version** and **unknown algorithm id** | `UnsupportedFormatException` (the base; no subclass needed) — a newer writer's output must not be quarantined |
| `DecryptionException` for a **disallowed keyId** | stays `DecryptionException`. Judgement call: the data may be fine for a reader with a different allowlist, but this is a security admission decision, and "hold and route to a reader that would accept it" is what an attacker steering keyIds wants |
| `KeyAccessException extends IllegalStateException` | `extends TransientCodecException` |
| `EncryptionException extends IllegalStateException` — provider or strategy failure during encode, invalid data key on encode | `extends TransientCodecException`; honest about what it is |
| decode: cipher set-up failure (`KeyAccessException`), admission predicate throwing, key accessors throwing | unchanged in kind; now `TransientCodecException` by inheritance |

Spec 006's decode invariant becomes: `decode(byte[])` may only throw
`InvalidPayloadException`, `UnsupportedFormatException`, or
`TransientCodecException` (each possibly via its crypto subclass). The fuzz
target's property is updated to match.

### `codec-versioned` (amends spec 007 §Errors)

| Today | Becomes |
|---|---|
| `VersionedFormatException extends RuntimeException` — short buffer, bad magic | `extends InvalidPayloadException` |
| `UnknownVersionException extends VersionedFormatException` | `extends UnsupportedFormatException`; keeps `version()`. **It is no longer a subtype of `VersionedFormatException`** — that inheritance was the trap this spec exists to remove: a `catch (VersionedFormatException) → dead-letter` policy would have discarded every message during a rolling upgrade |

### `codec-transforms`, `codec-zstd`, `codec-lz4`

| Today | Becomes |
|---|---|
| `CompressionStreamCodec.encode`: `UncheckedIOException` | `TransientCodecException` wrapping the `IOException` — a compressor failing on valid input is not the value's fault |
| `CompressionStreamCodec.decode`: `UncheckedIOException` on a corrupt stream | `InvalidPayloadException` wrapping the `IOException` |
| `CompressionStreamCodec.decode`: `IllegalStateException` past the decoded-size cap | `InvalidPayloadException` — a payload that expands past the cap is hostile input |
| `ChecksumCodec.decode`: `IllegalArgumentException` (too short, mismatch) | `InvalidPayloadException` |
| `ChecksumCodec`: `IllegalStateException` for a `Checksum` wider than 32 bits | `TransientCodecException` — a misconfigured dependency |
| `Base32Codec`, `Base64Codec`, `HexCodec` decode: `IllegalArgumentException` (own or `java.util.Base64`'s) | `InvalidPayloadException`, library exception as cause where there is one |
| `StringCodec.decode`: `IllegalArgumentException` wrapping `CharacterCodingException` | `InvalidPayloadException`, cause preserved |
| `StringCodec.encode` on an unmappable character (if the charset encoder reports it) | `InvalidValueException` |

### Backends

| Module | `decode` failure today | Becomes | `encode` failure today | Becomes |
|---|---|---|---|---|
| `codec-jackson` (3.x) | `JacksonException` propagates | `InvalidPayloadException`, cause preserved | `JacksonException` propagates | `InvalidValueException`, cause preserved |
| `codec-jackson2` | `UncheckedIOException` | `InvalidPayloadException`, cause = the `IOException` | `UncheckedIOException` | `InvalidValueException`, cause = the `JsonProcessingException` |
| `codec-gson` | `JsonSyntaxException` / `JsonParseException` propagate | `InvalidPayloadException` | `JsonIOException` propagates | `InvalidValueException` |
| `codec-jsonb` | JSON-P `JsonParsingException` / `JsonbException` propagate (see the getting-started note) | `InvalidPayloadException` — and the getting-started caveat goes away | `JsonbException` propagates | `InvalidValueException` |
| `codec-protobuf` | `IllegalArgumentException` wrapping `InvalidProtocolBufferException` | `InvalidPayloadException`, cause preserved | — (a generated message always encodes) | — |
| `codec-fory` | `DeserializationException` / `InsecureException` propagate; `ClassCastException` on wrong type | `InvalidPayloadException` for all three, cause preserved. An `InsecureException` (depth, memory, unknown class) is hostile or foreign input, not infrastructure | `ForyException` on an unregistered nested type | `InvalidValueException` |

Each backend catches the *library's* root exception type only — never
`RuntimeException` — so a genuine bug in the codec still surfaces as itself.

## `Codec` Javadoc (amends spec 001)

`encode` gains `@throws InvalidValueException` and `@throws
TransientCodecException`; `decode` gains `@throws InvalidPayloadException`,
`@throws UnsupportedFormatException` and `@throws TransientCodecException`.
The interface Javadoc gains a paragraph stating the principle and pointing at
the guide section.

## Documentation

- A new guide section, **Handling failures**, in `docs/guides/composition.md`
  (or its own page if it outgrows a section): the four catch blocks, one
  sentence each on when they fire, the "subclass by what the caller does next"
  rule, and the two boundaries above. `docs/guides/encryption.md`'s error
  taxonomy section and `docs/guides/threat-model.md` are updated to place the
  crypto exceptions in the families; the getting-started JSON-B caveat is
  removed.
- CHANGELOG: a **Breaking changes** entry. Anything catching
  `IllegalArgumentException`, `IllegalStateException`, `UncheckedIOException`,
  or a library exception from `encode`/`decode` must catch the family (or
  `CodecException`) instead; the crypto exceptions keep their names but change
  superclass; `UnknownVersionException` is no longer a `VersionedFormatException`.

## Testing

- `codec-core`: the four families and the root exist, are unchecked, preserve
  message and cause, and `CodecException` has no other direct subclasses in the
  reactor (a reflection-free check: each module's own tests assert its
  exceptions' superclasses).
- Every module: for each row in the mapping, one test that the named condition
  throws the named family with the library exception as cause where the table
  says so. The existing tests that assert the old types are rewritten, not
  deleted — the conditions are the same, only the type changes.
- `codec-crypto`: `ExceptionTaxonomyTest` asserts the new superclasses; the
  decode fuzz target's property becomes "only `InvalidPayloadException`,
  `UnsupportedFormatException` or `TransientCodecException` escape"; unknown
  version and unknown algorithm id are asserted as `UnsupportedFormatException`
  and *not* `InvalidPayloadException`.
- `codec-versioned`: `UnknownVersionException` is asserted to be an
  `UnsupportedFormatException` and *not* a `VersionedFormatException` or
  `InvalidPayloadException`.
- `codec-kafka`, `codec-spring-data-redis`: a `CodecException` from the codec
  propagates unchanged through the adapter.

## Out of scope

- Mapping onto Kafka's `SerializationException` or Spring's
  `SerializationException` in the adapters (a follow-up, if wanted).
- Any change to messages: the crypto uniform-message policy, the sanitised
  keyId echo, and every existing message text stay as they are.
- Any change to construction-time exceptions.

## Definition of done

- `codec-core` ships the five classes with full Javadoc; `Codec`'s
  `encode`/`decode` document their throws.
- Every row of the mapping is implemented and tested; no `encode`/`decode`
  path in the reactor throws outside the hierarchy except `NullPointerException`
  for a null argument.
- Specs 001, 005, 006 and 007 carry an amendment note pointing here.
- Guide section, encryption/threat-model updates, getting-started caveat
  removed, CHANGELOG breaking-change entry.
- `./mvnw -Pci -B clean verify` green, including PIT on `codec-crypto`.
