# Spec 005 — codec-crypto: envelope encryption transform

Date: 2026-08-24
Status: draft, revised after adversarial review; pending maintainer review

## Purpose

A `Codec<byte[]>` transform providing authenticated encryption through the
existing `andThen` composition seam, with envelope encryption and runtime key
management. Consumers get a correct AES-GCM implementation instead of writing
their own; key access is an SPI so in-process JCE keys and remote KMS providers
(AWS KMS, Vault, GCP KMS) both fit.

```java
Codec<Order> codec = factory.create(Order.class)
    .andThen(new GzipCodec())
    .andThen(EnvelopeCodec.builder(provider).build());
```

## Module

`codec-crypto`, new module depending only on `codec-core`. Zero external
dependencies: all cryptography is JCE (JDK-provided). KMS bindings are NOT part
of this module — consumers implement `DataKeyProvider` against their KMS SDK,
or a future `codec-crypto-aws`-style module can ship one.

Package: `org.jwcarman.codec.crypto`. Automatic-Module-Name:
`org.jwcarman.codec.crypto`.

## Components

### DataKeyProvider (SPI — consumers implement)

```java
public interface DataKeyProvider {
  DataKey newDataKey();
  SecretKey unwrap(String keyId, byte[] wrapped);
  default boolean allowsKeyId(String keyId) { return true; }
}
```

`allowsKeyId` is the provider's own admission check, consulted by
`EnvelopeCodec` before `unwrap` unless the builder's predicate overrides it.
Putting the default in the SPI puts the question in front of the party that
knows the answer: `JceDataKeyProvider` overrides it from its key map, and a KMS
implementor sees the method in the interface they are implementing — an
interface method gets read where a Javadoc MUST gets skimmed.

`newDataKey` maps onto AWS KMS `GenerateDataKey` and Vault
`transit/datakey/plaintext`, which return plaintext and wrapped DEK in one
call. GCP Cloud KMS has no data-key operation; its documented envelope pattern
is generate-locally-then-Encrypt, which is also one round trip inside
`newDataKey()` — implementors on GCP are using the SPI as intended. `unwrap`
maps onto the KMS decrypt operation.

**Security contract (Javadoc, normative):** `unwrap` receives a `keyId` and
`wrapped` blob read from unauthenticated ciphertext — the GCM tag cannot be
verified until the DEK is recovered, so these bytes are attacker-controlled at
the time of the call. Implementations MUST (a) pass the supplied `keyId` to the
KMS as the key restriction (e.g. the `KeyId` parameter of AWS KMS `Decrypt` —
never let the wrapped blob select its own key), and (b) reject any `keyId`
outside the set of KEKs the application intends to trust. Without this, an
attacker who knows the plaintext of any DEK wrapped under any KEK the
application is *permitted* to decrypt can forge messages that verify.
`EnvelopeCodec` additionally enforces an allowlist before calling `unwrap`
(below); the contract exists so a provider used outside `EnvelopeCodec` is not
silently unsafe.

**Caching contract (Javadoc):** implementations MAY cache `unwrap` results
keyed by `(keyId, hash(wrapped))` — where the hash is collision-resistant
(e.g. SHA-256) or the wrapped bytes themselves — to avoid a network round trip
per decode; a cache MUST be bounded in entries and time. A caching decorator is deliberately
deferred from v1 (see Out of scope) — this contract makes consumer-side caching
legitimate in the meantime.

Providers must be thread-safe (documented on the SPI).

### DataKey

```java
public record DataKey(String keyId, SecretKey key, byte[] wrapped) {}
```

Canonical constructor validates non-null fields, keyId UTF-8 length in
[1, 65535], wrapped length in [1, 65535], and **clones `wrapped`**; the
accessor returns a clone. `equals`/`hashCode` are overridden for content
equality on `keyId` and `wrapped` only; `toString` prints keyId and wrapped
length, never key material.

### DataKeyStrategy (SPI — lifecycle policy, ships two implementations)

```java
public interface DataKeyStrategy {
  DataKey acquire(DataKeyProvider provider);
}
```

The strategy is pure policy over a provider the codec owns and passes in —
there is exactly one provider per codec, used for both encode (via the
strategy) and decode (directly). This removes any possibility of
encode/decode provider mismatch.

- `DirectDataKeyStrategy` (default): `provider.newDataKey()` per message.
  Stateless. Maximum key isolation; right default for JCE and the conservative
  choice for KMS.
- `BoundedDataKeyStrategy` (opt-in): caches a DEK, rolls after N messages or
  duration T, whichever first. Both bounds required at construction. Message
  cap (a `long`) validated in [1, 2^24] — collision probability ~2^-49 at the
  ceiling, well clear of NIST SP 800-38D's 2^32 random-nonce limit — with 2^20
  as the documented recommended value (~2^-57). The shipped implementation
  deliberately does not allow configuration up to the NIST cliff edge; a
  consumer who genuinely needs more implements the strategy SPI, which is what
  it is for. The bound counts messages, not bytes; the documented assumption is
  messages well below GCM's 64 GiB per-invocation plaintext limit. Duration is
  measured on a monotonic ticker injected as a `LongSupplier` (production
  default `System::nanoTime`; test seam, same pattern as the `secureRandom`
  builder seam). Bounds are enforced with atomics; a roll under contention may
  occur one message early, never late.
  Documented caveat: in environments where a VM or container may be snapshotted
  and cloned, duplicated `SecureRandom` state can repeat nonces under a shared
  cached DEK — prefer `DirectDataKeyStrategy` there, or roll on resume.
  A retired DEK is simply released to GC — no
  `destroy()` call and no close hook (OpenJDK's `SecretKeySpec` does not
  implement destruction, and destroying a key another thread may still be
  encrypting with is a use-after-destroy race; in-flight operations holding a
  retired `DataKey` remain valid). Provider failure during roll fails the
  encode — never silent reuse of an expired DEK.

The strategy SPI is the escape hatch: consumers with different requirements
implement their own without touching EnvelopeCodec.

**Docs requirement:** the cost of the default with a remote KMS — one network
call per encode, one per decode absent provider caching — must be stated
loudly, so bounding is a deliberate opt-in rather than a discovery on the
first bill.

### JceDataKeyProvider (ships in the module)

In-process provider for consumers without a KMS. Holds one or more KEKs
(`SecretKey`, AES-256) supplied at construction as a `Map<String, SecretKey>`
plus the id of the current wrapping KEK; generates AES-256 DEKs from
`SecureRandom`; wraps with AES key-wrap (`AESWrap`, RFC 3394 — deterministic,
no nonce management, integrity-checked via its ICV). New messages wrap under
the current KEK; `unwrap` resolves any KEK in the map and throws for any keyId
not in the map — the map IS the allowlist, satisfying the security contract.
A constructor overload accepts a `SecureRandom` (test seam; production uses a
default instance).

### EnvelopeCodec (implements Codec<byte[]>)

Owns the wire format, nonce generation, the GCM calls, and decode-side keyId
admission. Knows nothing about key origin. Built via builder — the option set
(strategy, AAD, allowlist, SecureRandom) makes telescoping constructors
unworkable:

```java
EnvelopeCodec.builder(provider)          // required
    .strategy(DataKeyStrategy)           // default: DirectDataKeyStrategy
    .aad(byte[])                         // default: none; defensively copied
    .allowedKeyIds(Predicate<String>)    // default: provider.allowsKeyId (see below)
    .secureRandom(SecureRandom)          // default: new SecureRandom(); test seam
    .build();
```

Decode-side admission, evaluated against the wire keyId before `unwrap`: the
builder's `allowedKeyIds` predicate when set, otherwise the provider's
`allowsKeyId`. Deny-all-by-default was considered and rejected (it would fail
every JCE round trip out of the box); pure allow-all was rejected in review
because a KMS provider that skips its contract's allowlist clause would leave
both layers open — the SPI default method puts the admission question on the
party that knows the answer.

AAD is fixed per instance. Per-message-varying context is out of scope for v1
because the `Codec<byte[]>` seam has no parameter to carry it — a context-aware
API is a codec-core seam change, not a codec-crypto option. **Documented
consequence (docs requirement):** per-message context binding is the standard
defense against ciphertext substitution — an attacker with write access to a
datastore swapping user A's encrypted field into user B's row, which this
module's decode will accept cleanly under a shared codec and KEK. Consumers
storing per-record encrypted fields should construct a codec per context where
rows must not be interchangeable, or bind identity inside the plaintext and
verify after decode. This limitation and mitigation MUST appear in the module
documentation, not only here.

`.aad(byte[])` rejects an empty array: zero-length AAD and absent AAD produce
identical GCM tags, so accepting empty would create two spellings of the same
cryptographic configuration and an ill-defined absent-vs-empty test boundary.

## Algorithm

AES-256-GCM, 96-bit random nonce from `SecureRandom`, 128-bit tag. Algorithm id
is carried in the header so alternatives can be added under new ids without
breaking stored data.

Known, documented limitation: GCM is not key-committing. Because the wrapped
DEK travels with the message, a sophisticated attacker in multi-party scenarios
can construct a ciphertext valid under two different DEKs ("invisible
salamander"). The keyId allowlist mitigates the practical variants; algorithm
id `0x02` is **reserved** for a key-committing suite (GCM plus key-commitment
tag) should the threat model ever warrant it.

### Post-quantum considerations

The v1 envelope is symmetric end-to-end: AES-256-GCM payload encryption,
AES-256 DEKs, AES key-wrap in the JCE provider, and symmetric KEKs in the
common KMS configurations. Symmetric cryptography at 256-bit keys is considered
quantum-resistant (Grover's algorithm reduces effective strength to ~128 bits,
which remains the accepted floor), so there is no harvest-now-decrypt-later
exposure in the formats this module produces. A provider that wraps DEKs with
asymmetric RSA/ECC keys introduces quantum risk in *its* wrapped blobs; the SPI
treats wrapped bytes as opaque, so a post-quantum KEM provider (ML-KEM, in the
JDK since 24) plugs in with no wire-format change.

## Wire format (version 1)

Permanent once anyone persists ciphertext. **Docs requirement:** the module
documentation must state plainly that this format is proprietary to
codec-crypto and permanent — nothing else reads it, and cross-language readers
are a non-goal; the frozen test vector is the format's conformance spec. All multi-byte integers big-endian;
length fields are read as **unsigned** 16-bit values.

| offset  | field                              | bytes |
|---------|------------------------------------|-------|
| 0       | magic `0x4A 0x43` ("JC")           | 2     |
| 2       | format version (`0x01`)            | 1     |
| 3       | algorithm id (`0x01` = AES-256-GCM)| 1     |
| 4       | keyId length (uint16, ≥ 1)         | 2     |
| 6       | keyId (UTF-8)                      | k     |
| 6+k     | wrapped DEK length (uint16, ≥ 1)   | 2     |
| 8+k     | wrapped DEK                        | w     |
| 8+k+w   | nonce                              | 12    |
| 20+k+w  | ciphertext ‖ GCM tag               | n+16  |

Overhead: `36 + k + w` bytes. Roughly 90 bytes for JCE with a short keyId,
~295 for a KMS ARN plus a 184-byte wrapped DEK. On small, numerous messages the
wrapped DEK dominates — the documented motivation for BoundedDataKeyStrategy
on encode and for the provider caching contract on decode.

**AAD construction:** bytes 0 through 19+k+w inclusive — everything before the
ciphertext — concatenated with the per-instance AAD when present. The
concatenation is unambiguous because the header region is self-delimiting (its
own length fields determine where it ends); any future format version must
preserve that property. What this buys, stated precisely: the version,
algorithm id, and keyId cannot be modified on a ciphertext that subsequently
verifies. It does NOT protect anything pre-verification — see the decode
order and the DataKeyProvider security contract. (The nonce is authenticated by
GCM's own construction regardless of AAD; the wrapped DEK, if tampered with,
fails at unwrap — AESWrap's ICV or the KMS's integrity check — before tag
verification is reached.)

Deliberate exclusions:
- No plaintext length field (derivable; a disagreeing prefix is attack surface).
- uint16 length fields, not uint32 (bounds both at 64KB; wider invites
  allocation attacks).
- keyIds are visible in ciphertext by necessity; document that keyIds must not
  contain secrets.

**Decode order and validation:**
1. Total length ≥ 38 (minimum header with k=1, w=1, plus 16-byte tag for empty
   plaintext).
2. Magic, else reject (fast, clear rejection of non-encrypted input).
3. Version and algorithm against known values.
4. keyId length ≥ 1 and in-bounds for the remaining buffer; same for wrapped
   length; ciphertext region ≥ 16 bytes. All bounds checked before allocation.
5. `allowedKeyIds` predicate on the parsed keyId.
6. `provider.unwrap` — the first point key material or a provider is touched.
7. GCM decrypt + tag verification.

## Error handling

Consistent with the rest of the codebase: every failure throws, nothing logs.

- `DecryptionException` (extends `IllegalArgumentException`): "this data is
  bad." Structural failures (bad magic, unknown version/algorithm, bounds
  violations, disallowed keyId) carry stage-specific messages; cryptographic
  rejections (tag mismatch, unwrap *rejection* — AESWrap ICV failure, KMS
  invalid-ciphertext) share one indistinguishable message. Scope of that claim:
  exception *content* only. The timing side channel is unavoidable — a KMS
  unwrap round trip and a local tag check differ observably, and structural
  rejections return before any provider call — and is documented as such.
- `KeyAccessException` (extends `IllegalStateException`): "the key
  infrastructure is unavailable" — timeouts, throttling, credential expiry, or
  any other provider failure that does not assert the ciphertext is invalid.
  Cause preserved. The distinction is normative on the `DataKeyProvider`
  contract: `unwrap` MUST throw `DecryptionException` only when the KMS/JCE
  layer affirmatively rejected the blob, and let availability failures
  propagate (the codec wraps them in `KeyAccessException`). Rationale: a
  pipeline that quarantines or discards on `DecryptionException` must never do
  so because a KMS was briefly down — conflating the two turns an availability
  blip into data loss.
- `EncryptionException` (extends `IllegalStateException`): provider or strategy
  failure during encode, wrapping the cause.

All three live in `org.jwcarman.codec.crypto`.

## Thread-safety

- `EnvelopeCodec`: stateless per call; thread-safe. `Cipher` instances are
  created per invocation (Cipher is not thread-safe). One `SecureRandom`
  (thread-safe) per codec.
- `DirectDataKeyStrategy`: stateless.
- `BoundedDataKeyStrategy`: as specified above; retired DEKs remain usable by
  in-flight operations (no destruction).
- Providers must be thread-safe; documented on the SPI.

## Ordering and composition notes (documentation requirements)

- Compress-then-encrypt is the correct order (`.andThen(gzip).andThen(crypto)`);
  encrypting first makes compression useless. Document explicitly.
- CRIME/BREACH caveat: compressing attacker-influenced plaintext alongside
  secrets can leak length information. Documented, not enforced.

## Testing

- Round-trip property tests through the full chain: serialize → gzip → encrypt
  → decrypt → gunzip → deserialize, across payload sizes including empty.
- Tamper matrix: flip each header field and one ciphertext byte; truncate the
  tag; append a trailing byte; assert `DecryptionException` for every mutation.
- KeyId admission: a wire keyId rejected by the builder predicate, by the
  provider's `allowsKeyId`, or absent from the JCE map fails **without**
  `unwrap` being invoked — asserted with a counting provider; builder predicate
  overrides a permissive provider and vice versa is not consulted when the
  predicate is set.
- Cross-provider round trip: encrypt via `JceDataKeyProvider`, decrypt via a
  test fake sharing the wrapped form — proves the header carries everything
  decrypt needs.
- Strategy: bounded rolls at message cap and at duration (via the injected
  ticker); cap validated in [1, 2^24]; provider failure during roll surfaces as
  EncryptionException; concurrent encode during roll completes correctly.
- AAD: mismatch between encode-side and decode-side AAD fails; absent-vs-present
  fails both directions; `.aad(new byte[0])` is rejected at build time.
- Error taxonomy: a provider throwing a timeout from `unwrap` surfaces as
  `KeyAccessException`, not `DecryptionException`; AESWrap ICV failure surfaces
  as `DecryptionException`.
- `DataKey`: mutation of the array passed to the constructor, or of the array
  returned by the accessor, does not affect the record.
- Frozen test vector: fixed KEK, deterministic `SecureRandom` injected via the
  builder seam and the `JceDataKeyProvider` overload (pins nonce and DEK
  bytes), expected ciphertext committed. A refactor that changes the wire
  format fails this test.

## Out of scope (v1)

- Per-message AAD / encryption context (fixed per instance only)
- KMS binding modules (SPI only; consumers implement)
- An unwrap-caching decorator — the SPI contract explicitly permits provider-
  side caching and defines its rules; shipping a decorator is deferred until a
  consumer needs it rather than designing cache eviction speculatively
- A published provider contract-test kit (TCK): valuable — it would turn the
  SPI's normative MUSTs into failing tests for third-party providers — but
  shipping it means either a JUnit dependency in the main jar (violating
  zero-dependency) or a separate test-fixtures artifact; deferred until a
  second real provider exists. The `allowsKeyId` SPI method covers the most
  dangerous omission it would have caught.
- Streaming encryption (matches existing non-goal)
- Key-committing algorithm suite (id 0x02 reserved; documented limitation)
- Spring Boot auto-configuration (no sensible default for key material; may
  revisit if demand appears)

## Definition of done

- [ ] All components implemented as specified
- [ ] All tests above pass (`./mvnw -Pci verify` green, including dependency
      gates — module must add zero allowlist entries)
- [ ] Spotless passes; license headers present
- [ ] Wire format documented in docs site
- [ ] PRD updated: crypto module listed, stale `Codec.type()` /
      `StringCodec`/`ByteArrayCodec` references corrected
