# Threat Model

This page documents what `codec-crypto`'s envelope encryption defends against,
what it deliberately does not, and how that claim is checked. It is written
for two audiences: consumers deciding whether the module fits their threat
model, and an outside reviewer auditing the implementation against it.

## Assets

- **Plaintext** — the data passed to `EnvelopeCodec.encode` before
  compression or encryption, and returned from `decode`.
- **Data-encryption keys (DEKs)** — the per-message AES-256 keys that encrypt
  plaintext under AES-256-GCM. Generated fresh by default; possibly reused
  across a bounded window under `BoundedDataKeyStrategy`.
- **Key-encryption keys (KEKs)** — the keys a `DataKeyProvider` holds to wrap
  and unwrap DEKs. For `JceDataKeyProvider` these are AES-256 keys supplied at
  construction; for a KMS-backed provider they never leave the KMS.
- **The keyId → KEK trust set** — the mapping a `DataKeyProvider` consults to
  decide which KEKs it will actually use to unwrap. This is the allowlist
  described in [Key rotation](encryption.md#key-rotation-via-keyids); its
  integrity is what stands between "this application trusts this KEK" and "an
  attacker who names a KEK gets it used."

## Trust boundaries

```
EnvelopeCodec  <->  DataKeyProvider  <->  KMS
      ^                                    |
      |                                    v
consumer datastore  <-------------  wrapped DEK (in ciphertext)
      |
      v
   logs
```

`EnvelopeCodec` trusts `DataKeyProvider` to enforce its security contract (see
[Implementing DataKeyProvider against a KMS](encryption.md#implementing-datakeyprovider-against-a-kms))
and trusts nothing else about the environment. The consumer datastore and any
logging infrastructure are outside the module's control — `codec-crypto`
never logs, but a consumer's own logging around it is a boundary this module
cannot see across.

## Attacker capabilities considered

The following are assumed available to an attacker, individually or in
combination:

- **Reads ciphertext** — access to persisted `EnvelopeCodec` output, e.g. a
  database read or a backup.
- **Writes or substitutes ciphertext** in the consumer's datastore — able to
  replace one stored message with another valid message, including one it
  captured from elsewhere in the same store.
- **Holds a KEK the application is *permitted* but not *intended* to use** —
  for example, a KEK belonging to a different tenant or environment that the
  application's KMS credentials can technically reach.
- **Reads application logs.**
- **Snapshots and clones the VM or container** the application runs in,
  including in-memory RNG state.

## Defended

| Threat | Mechanism |
|---|---|
| Ciphertext tampering — payload or header fields modified in storage or transit | AES-256-GCM authenticates the payload; the header (version, algorithm id, keyId, wrapped DEK, nonce) is authenticated as additional authenticated data (AAD), so modifying any of it invalidates the GCM tag. See [Wire format](encryption.md#wire-format). |
| An attacker-supplied keyId selecting an untrusted KEK | `EnvelopeCodec` evaluates the `allowedKeyIds` predicate (or the provider's `allowsKeyId`) against the wire keyId and rejects before `unwrap` is ever called — admission strictly precedes key access. |
| Distinguishing failure modes to learn about the ciphertext | Every cryptographic rejection — GCM tag mismatch, AES-KW ICV failure, a KMS's own invalid-ciphertext response — surfaces as the same `DecryptionException` message, `"Unable to decrypt data"`. |
| Oversized or malformed length fields driving excessive allocation | All length fields (keyId length, wrapped-DEK length, derived ciphertext region) are bounds-checked against the remaining buffer before any array is allocated and before the provider is called. |
| Nonce reuse under a shared key | The default `DirectDataKeyStrategy` draws a fresh DEK from the provider on every `encode`, so no two messages share a key by default. |
| Conflating "the ciphertext is bad" with "the key infrastructure is unreachable" | `KeyAccessException` (KMS timeout, throttling, credential expiry) is distinct from `DecryptionException` (affirmative cryptographic rejection); a consumer pipeline can retry the former and quarantine only the latter. See [Error taxonomy](encryption.md#error-taxonomy). |

## Not defended

| Threat | Consumer-side mitigation |
|---|---|
| **Ciphertext substitution between records** under one codec instance and KEK — an attacker with datastore write access swaps row A's encrypted field into row B; both were encrypted under the same AAD, so decode accepts the swap cleanly. | Construct a distinct `EnvelopeCodec` per context where rows must not be interchangeable, or bind an identity value inside the plaintext itself and verify it after decode. |
| **Timing side channel** between structural rejection (fast, no provider call) and cryptographic rejection after a KMS round trip. | Not addressable at this layer; do not build timing-independent guarantees on top of `codec-crypto` alone. |
| **Nonce reuse under cloned RNG state** — a VM or container snapshot-and-clone can duplicate `SecureRandom` state, which can repeat nonces under a `BoundedDataKeyStrategy`-cached DEK. | Prefer `DirectDataKeyStrategy` in snapshot/clone-capable environments, or explicitly roll the bounded strategy's key on resume. |
| **A `DataKeyProvider` that violates its own contract** — e.g. an `unwrap` implementation that doesn't restrict the KMS decrypt call to the supplied keyId, or that returns a key for a keyId it hasn't verified is trusted. | `EnvelopeCodec`'s own admission check is a second layer, not a substitute; provider implementations must independently honor the SPI's normative contract. |
| **Key commitment** — AES-GCM is not key-committing; in principle a ciphertext could be constructed that decrypts under two different DEKs to different plaintexts ("invisible salamander"). | Algorithm id `0x02` is reserved for a future key-committing suite; not built in v1 because the single-wrapped-DEK envelope with keyId admission does not expose the multi-recipient surface that motivates that construction elsewhere. |

## Assurance status

- **Known-answer tests**: `GcmKnownAnswerTest` against NIST CAVP GCM vectors
  (AES-256, 96-bit IV, 128-bit tag, with AAD — encrypt and marked-FAIL decrypt
  cases) and `AesKeyWrapKnownAnswerTest` against the RFC 3394 §4.6 vector,
  both driven through the module's own production code paths
  (`EnvelopeCodec.gcmEncrypt`/`gcmDecrypt`, `JceDataKeyProvider.newDataKey`) —
  not a reimplementation alongside it.
- **Decoder fuzzing**: `EnvelopeCodecDecodeFuzzTest` asserts that `decode`
  only ever throws `DecryptionException` (the only legitimate outcome from
  the in-process `JceDataKeyProvider`); `EnvelopeCodecMutationFuzzTest`
  asserts that encode-then-mutate either round-trips or is rejected. Each
  target is its own class, run in its own forked JVM, because jazzer-junit
  fuzzes only the first `@FuzzTest` per JVM. A committed seed corpus runs in
  regression mode with every normal test run; the `-Pfuzz` Maven profile
  fuzzes both targets live, 120 seconds each.
- **Mutation testing**: PIT runs in the `ci` profile with an 85% mutation and
  90% line-coverage threshold. The current score is 149/149 mutations killed,
  a run the maintainer verified stable across repeated runs.
- **Static analysis**: SpotBugs with the findsecbugs plugin runs in the `ci`
  profile at `effort=Max`, `threshold=Low`. One finding is excluded:
  `CIPHER_INTEGRITY` on `JceDataKeyProvider.wrapCipher`, a documented false
  positive — findsecbugs' integrity detector doesn't recognize AES key wrap
  (RFC 3394) as integrity-protected, though its 64-bit ICV is verified on
  every unwrap. The exclusion is recorded in
  `codec-crypto/spotbugs-exclude.xml` and in this repository's `CLAUDE.md`; it
  is the only sanctioned suppression anywhere in the codebase.
- **Adversarial review history**: spec 005 was revised after adversarial
  review before this module's implementation began, and this assurance
  program (spec 006) added known-answer tests, fuzzing, mutation testing, and
  static analysis specifically to check the implementation against external
  references and mechanical adversaries rather than against its own tests.

## Independent review checklist

An outside cryptographer auditing this module should verify, against the
source directly:

- The AAD span on both encode and decode is bytes `0` through `19+k+w`
  inclusive of the header — everything before the ciphertext — followed by
  the per-instance AAD when present.
- KeyId admission (`allowedKeyIds` / `allowsKeyId`) is evaluated strictly
  before `unwrap` is called, on every decode path.
- All length fields are bounds-checked against the remaining buffer before
  any allocation and before any provider call.
- The uniform cryptographic-failure message (`"Unable to decrypt data"`) is
  shared by every cryptographic rejection — GCM tag mismatch, AES-KW ICV
  failure, and any provider-level unwrap rejection — with no
  distinguishing detail in the message.
- The nonce is 12 random bytes drawn from `SecureRandom` on every encode, not
  derived or reused.
- `JceDataKeyProvider` wraps with AES-KW under a 256-bit KEK and rejects any
  blob shorter than 2 bytes or tagged with an unrecognized wrap-scheme byte.
- `BoundedDataKeyStrategy`'s message cap is enforced at or below `2^24`, and
  its duration bound is a monotonic subtraction (no wraparound or
  wall-clock-adjustment exposure).
- No exception message and no `toString` implementation anywhere in the
  module includes key material.

## Where next

- [Encryption](encryption.md) — the full `codec-crypto` guide: quickstart,
  key rotation, data-key strategies, wire format, and error taxonomy
- [Security Policy](https://github.com/jwcarman/codec/blob/main/SECURITY.md) —
  how to report a vulnerability
