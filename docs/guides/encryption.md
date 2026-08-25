# Encryption

`codec-crypto` provides `EnvelopeCodec`, a `Codec<byte[]>` transform that
layers AES-256-GCM envelope encryption onto any codec through `andThen` — the
same composition seam used for compression.

## Quickstart

Add the module:

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-crypto</artifactId>
    <version>0.4.0</version>
</dependency>
```

`codec-crypto` has zero external dependencies — all cryptography is JCE, built
into the JDK. It ships one in-process key provider, `JceDataKeyProvider`, for
consumers without a KMS:

```java
SecretKey kek = ...; // an AES-256 key you manage
DataKeyProvider provider =
    new JceDataKeyProvider("kek-2026-08", Map.of("kek-2026-08", kek));

Codec<Order> codec =
    codecFactory.create(Order.class)
        .andThen(new GzipCodec())
        .andThen(EnvelopeCodec.builder(provider).build());

byte[] wire = codec.encode(order);
Order restored = codec.decode(wire);
```

`EnvelopeCodec` is built through `EnvelopeCodec.builder(provider)` — the option
set (key-acquisition strategy, AAD, keyId allowlist, `SecureRandom`) makes a
plain constructor unworkable.

## The envelope model

Every message gets its own data-encryption key (DEK), an AES-256 key generated
fresh (or reused, depending on strategy — see below). The DEK encrypts the
payload with AES-256-GCM. The DEK itself is then wrapped by a
key-encryption key (KEK) held by a `DataKeyProvider`, and the wrapped form
travels alongside the ciphertext in the message. Decode reverses this: unwrap
the DEK using the KEK identified by the message's keyId, then GCM-decrypt the
payload.

This is standard envelope encryption. It means the KEK itself never appears in
the message, and a `DataKeyProvider` backed by a KMS never sees plaintext
payload data — only the small wrapped DEK.

What travels in the message: the format version and algorithm id, a keyId
identifying which KEK wrapped the DEK, the wrapped DEK itself, the GCM nonce,
and the ciphertext plus its authentication tag. See [Wire format](#wire-format)
below for the exact layout.

## Key rotation via keyIds

Rotation happens at the KEK level, using keyIds — not by re-keying every
message. `JceDataKeyProvider` is constructed with a `Map<String, SecretKey>` of
every KEK it trusts, plus the id of the *current* KEK used to wrap new DEKs:

```java
DataKeyProvider provider =
    new JceDataKeyProvider(
        "kek-2026-09",
        Map.of(
            "kek-2026-08", oldKek,
            "kek-2026-09", newKek));
```

New messages wrap their DEK under `kek-2026-09`. Messages already encrypted
under `kek-2026-08` still decode, because `unwrap` accepts any keyId present in
the map. Once every message wrapped under an old KEK has been re-encrypted (or
has aged out), drop it from the map.

The KEK map doubles as the provider's admission allowlist: `unwrap` and
`allowsKeyId` both consult it directly, so a keyId absent from the map is
rejected before decryption is attempted.

## Choosing a data-key strategy

`DataKeyStrategy` decides how a DEK is acquired for each message. Two ship with
the module.

`DirectDataKeyStrategy` is the default: it calls `provider.newDataKey()` on
every `encode`. Every message gets an independent DEK, so there is no shared
key under which two nonces could ever collide — this is the safest option and
the right default.

!!! warning "The default costs one KMS round trip per message"
    If your `DataKeyProvider` is backed by a remote KMS, `DirectDataKeyStrategy`
    means one network call to generate a DEK on every `encode`, and one more to
    unwrap it on every `decode`. For high-volume or latency-sensitive paths,
    that cost — and the wrapped-DEK bytes added to every message — adds up
    fast. Don't discover this on your first KMS bill; decide deliberately.

`BoundedDataKeyStrategy` is the opt-in alternative: it caches a DEK and rolls
to a fresh one after a message cap or a duration, whichever comes first. Both
bounds are required at construction:

```java
new BoundedDataKeyStrategy(1 << 20, Duration.ofMinutes(5));
```

The message cap is validated to `[1, 2^24]`. `2^20` (roughly one million
messages, ~2^-57 collision probability) is the documented recommended value.
The cap is deliberately capped below the `2^32` NIST SP 800-38D random-nonce
limit for a single key — even at the `2^24` ceiling the collision probability
is only about `2^-49`, but the class refuses to be configured any closer to
that cliff edge. A consumer that genuinely needs a higher cap implements
`DataKeyStrategy` directly.

!!! danger "Not safe under VM/container snapshot-and-clone"
    If your runtime may snapshot and clone a live process (some serverless or
    sandboxed platforms do this), a clone can resume with an identical cached
    DEK and duplicated `SecureRandom` state — which can make nonces repeat
    under that shared key. Prefer `DirectDataKeyStrategy` in such environments,
    or explicitly roll the strategy's key on resume from a snapshot.

## Implementing `DataKeyProvider` against a KMS

`DataKeyProvider` is the SPI you implement to back `EnvelopeCodec` with a
remote KMS (AWS KMS, HashiCorp Vault, GCP Cloud KMS, ...). `codec-crypto` ships
no KMS bindings — this keeps the module dependency-free.

```java
public interface DataKeyProvider {
  DataKey newDataKey();
  SecretKey unwrap(String keyId, byte[] wrapped);
  default boolean allowsKeyId(String keyId) { return true; }
}
```

`newDataKey` maps onto operations that return a plaintext DEK and its wrapped
form in one round trip — AWS KMS `GenerateDataKey`, Vault
`transit/datakey/plaintext`. `unwrap` maps onto the corresponding decrypt
operation.

!!! danger "Security contract — MUST, not SHOULD"
    `unwrap` receives a `keyId` and `wrapped` blob read from *unauthenticated*
    ciphertext: the GCM tag can't be verified until the DEK is recovered, so at
    the time of this call both arguments are attacker-controlled. Implementations
    MUST:

    - pass the supplied `keyId` to the KMS as the key restriction for the
      decrypt call (e.g. the `KeyId` parameter of AWS KMS `Decrypt`) — never
      let the wrapped blob select its own unwrapping key; and
    - reject any `keyId` outside the set of KEKs the application intends to
      trust.

    Skip either one and an attacker who knows the plaintext of any DEK wrapped
    under any KEK your application is *permitted* to decrypt can forge messages
    that verify. `EnvelopeCodec` enforces its own allowlist before calling
    `unwrap` (see [Key rotation](#key-rotation-via-keyids)), but that does not
    make this contract optional — a provider used outside `EnvelopeCodec` must
    not be silently unsafe on its own.

`unwrap` implementations MAY cache results, keyed by `(keyId, hash(wrapped))`
or the wrapped bytes themselves, to avoid a network round trip per decode — any
such cache MUST be bounded in both entries and time. This is what makes
provider-side caching a legitimate way to blunt the round-trip cost noted
above, without codec-crypto having to ship and maintain a caching decorator.

!!! note "GCP Cloud KMS has no data-key operation"
    GCP's documented envelope pattern is generate-the-DEK-locally, then call
    `Encrypt` to wrap it. That's still a single round trip, and it fits
    entirely inside your `newDataKey()` implementation — you're using the SPI
    as intended, not working around it.

Implementations must also be thread-safe: a single provider instance is shared
across both encode and decode, potentially from multiple threads at once.

## Wire format

Version 1. Permanent once anyone persists ciphertext — this format is
**proprietary to codec-crypto**. Nothing outside this module reads it,
cross-language readers are a non-goal, and the frozen test vector
(`WireFormatVectorTest`) is the format's conformance spec: a refactor that
changes the wire format fails that test. All multi-byte integers are
big-endian; length fields are unsigned 16-bit values.

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

Overhead is `36 + k + w` bytes — roughly 90 bytes for a JCE provider with a
short keyId, and up to ~295 bytes for a KMS ARN plus a 184-byte wrapped DEK.
On small, frequent messages the wrapped DEK dominates that overhead, which is
part of why `BoundedDataKeyStrategy` and provider-side unwrap caching exist.

Decode checks the magic bytes as soon as the first 2 bytes are available and
rejects non-envelope input immediately, before the full minimum-length check
runs — so feeding `decode` something that isn't an `EnvelopeCodec` message (a
plain gzip stream, for example) fails fast on `bad magic`, not on a generic
length error.

## Ordering and composition

!!! tip "Compress before you encrypt"
    `.andThen(gzip).andThen(encryption)` — never the other order. Encrypted
    output is high-entropy ciphertext; it does not compress. Encrypting first
    makes the compression step pointless.

!!! warning "CRIME/BREACH: don't compress secrets next to attacker input"
    Compressing attacker-influenced plaintext in the same stream as a secret
    can leak information about that secret through the compressed length (the
    CRIME/BREACH class of attacks). `codec-crypto` does not defend against
    this — it's a property of compressing before encrypting, not of this
    module — so evaluate that risk independently before compressing untrusted
    input alongside sensitive data.

!!! danger "Ciphertext substitution: AAD is fixed per instance, not per message"
    AAD is fixed when you build the `EnvelopeCodec` — there is no per-message
    context parameter; the `Codec<byte[]>` seam has no place to carry one.
    The standard defense against ciphertext substitution — binding each
    message to the record or context it belongs to — is therefore *your*
    responsibility. Without it, an attacker with write access to a datastore
    can swap one row's encrypted field into another row under the same codec
    and KEK, and `decode` will accept it cleanly. If rows must not be
    interchangeable, either construct a distinct codec per context, or bind
    identity inside the plaintext and verify it after decode.

!!! warning "keyIds are not secret"
    The keyId travels in cleartext in every message — anyone who can read the
    ciphertext can read the keyId. Never encode secrets into a keyId.

## Error taxonomy

`codec-crypto` throws three exceptions, all in `org.jwcarman.codec.crypto`,
never logs:

- **`DecryptionException`** (extends `IllegalArgumentException`) — "this data
  is bad." Covers bad magic, unknown version/algorithm, bounds violations, a
  disallowed keyId, and cryptographic rejection (GCM tag mismatch, or a
  provider affirmatively rejecting the wrapped DEK). Cryptographic rejections
  all share the exact message `"Unable to decrypt data"`, deliberately
  indistinguishable from each other.
- **`KeyAccessException`** (extends `IllegalStateException`) — "the key
  infrastructure is unavailable": timeouts, throttling, credential expiry, or
  any other provider failure that does not assert the ciphertext itself is
  invalid. The cause is preserved.
- **`EncryptionException`** (extends `IllegalStateException`) — a provider or
  strategy failure during `encode`, wrapping the cause.

!!! danger "Never quarantine or discard data on KeyAccessException"
    `KeyAccessException` means the KMS was unreachable, not that the data is
    invalid. A pipeline that quarantines or discards ciphertext on any
    decryption failure must distinguish `KeyAccessException` from
    `DecryptionException` — conflating them turns a transient KMS outage into
    permanent data loss. Retain the encrypted data and retry.

This distinction is normative on `DataKeyProvider`: `unwrap` MUST throw
`DecryptionException` only when the KMS or JCE layer has affirmatively
rejected the blob as invalid; every other failure — including plain
availability failures — must propagate as an ordinary runtime exception, which
`EnvelopeCodec` wraps as `KeyAccessException`.

## Where next

- [Codec Composition](composition.md) — how `andThen` and transform chaining
  work in general
- [Getting Started](getting-started.md) — adding Codec to a project from
  scratch
