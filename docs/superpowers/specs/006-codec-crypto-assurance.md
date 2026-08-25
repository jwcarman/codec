# Spec 006 — codec-crypto: production assurance and agility seams

Date: 2026-08-25
Status: approved scope; supersedes nothing — amends 005 where noted

## Purpose

Raise codec-crypto from "reviewed" to "high confidence" before its first
release, without changing the envelope wire format. Two small additive API
seams that are painful to retrofit after release, plus an assurance program
that measures the implementation against external references and mechanical
adversaries rather than against itself.

Explicitly out of scope (decided 2026-08-25): an algorithm-suite registry and a
key-committing suite. The existing version and algorithm-id bytes already give
the format the property agility requires — a second suite can be added later
without a flag day. See "Future suites" below.

## Part 1 — Agility seams (amend spec 005)

### 1.1 JCE Provider injection

- `EnvelopeCodec.Builder.provider(java.security.Provider)` — optional. When
  set, every `Cipher.getInstance` in the codec passes the provider. Default:
  JDK provider lookup, unchanged.
- `JceDataKeyProvider.builder(String currentKeyId, Map<String, SecretKey> keks)`
  returning a builder with `.secureRandom(SecureRandom)`,
  `.provider(Provider)`, `.build()`. The two existing constructors remain and
  delegate. The provider governs the `AESWrap` lookups.
- Fail fast: both builders resolve their transform against the provider at
  build/construction time and throw `IllegalStateException` naming the
  transform if the provider cannot supply it. A configuration error must not
  surface later as a `DecryptionException` ("your data is bad").
- Rationale: FIPS-validated providers (BC-FIPS) can be selected per codec
  without installing them as the global highest-priority provider. All
  primitives used are FIPS-approved (AES-GCM, AES-KW).

### 1.2 Wrap-scheme tag in JceDataKeyProvider

- The wrapped blob becomes `[scheme:1][payload]`; scheme `0x01` = AES-KW
  (RFC 3394) over the 32-byte DEK, payload 40 bytes, blob 41 bytes.
- `unwrap` rejects a blob shorter than 2 bytes or with an unknown scheme via
  `DecryptionException.cryptographic` (uniform message).
- Invisible to `EnvelopeCodec` (the blob stays opaque). Unreleased, so no
  migration. Gives the zero-dependency provider the same migration story the
  KMS path has.
- Hygiene folded in: the transient DEK byte array is zeroed after the
  `SecretKeySpec` copies it.

### 1.3 Future suites (documentation only)

Spec 005's Algorithm section gains a paragraph: algorithm id `0x02` is
reserved for an AES-256-GCM key-committing suite (AWS Encryption SDK v2
construction: per-message 32-byte salt, HKDF-SHA256-derived encryption and
commitment keys, commitment verified constant-time before GCM). It would
occupy a suite-defined block between the nonce and the ciphertext, authenticated
as part of the header AAD. Not built now: the single-wrapped-DEK envelope with
keyId admission does not expose the multi-recipient surface that motivated
AWS's default; the id byte preserves the option.

## Part 2 — Assurance program

### 2.1 Known-answer tests

- The GCM call is extracted into package-private static helpers on
  `EnvelopeCodec` (`gcmEncrypt` / `gcmDecrypt`, taking provider, key, nonce,
  header AAD, optional extra AAD, data) and used by encode/decode — so the
  KATs exercise the exact code production uses.
- NIST CAVP GCM vectors (AES-256, 96-bit IV, 128-bit tag, with AAD; at least
  six encrypt vectors incl. empty plaintext, and at least two decrypt vectors
  marked FAIL). Provenance (file, Count) recorded in test comments.
- RFC 3394 §4.6 vector (256-bit KEK, 256-bit key data) driven through
  `JceDataKeyProvider.newDataKey()` with an injected `SecureRandom` that
  yields the key-data bytes; the blob minus its scheme byte must equal the
  RFC ciphertext. Vector text verified against the RFC by the implementer.

### 2.2 Decoder fuzzing

- Jazzer (`com.code-intelligence:jazzer-junit`, test scope — no compile
  dependency change). Targets: (a) `decode(byte[])` may only throw
  `DecryptionException` or `KeyAccessException`; (b) encode-then-mutate via
  `FuzzedDataProvider` must either round-trip (no mutation) or throw
  `DecryptionException`.
- Regression mode (corpus replay) runs in the normal test suite against a
  committed seed corpus (frozen vector, structural edge cases). A `fuzz`
  Maven profile runs live fuzzing with `JAZZER_FUZZ=1` for a bounded duration;
  the task runs it once for at least two minutes and reports.
- Any crash is a real bug: fix the implementation, add the crashing input to
  the corpus.

### 2.3 Mutation testing

- PIT (`pitest-maven` + `pitest-junit5-plugin`) in codec-crypto's `ci`
  profile, bound to `verify`: mutation threshold 85%, line-coverage threshold
  90%, build fails below. The fuzz test class is excluded from PIT's test
  set (non-deterministic runtime).
- Surviving mutants are addressed by strengthening tests, not by lowering the
  threshold. A survivor that is genuinely unkillable (equivalent mutant) is
  documented in the test suite with a comment naming it.

### 2.4 Static security analysis

- SpotBugs (`spotbugs-maven-plugin`) with `findsecbugs-plugin`, in
  codec-crypto's `ci` profile, `effort=Max`, `threshold=Low`, `failOnError`,
  `check` goal bound to `verify`. Findings are fixed in code. No exclusion
  filter file — the repository's no-suppression rule applies; a finding that
  cannot be resolved structurally is escalated, not filtered.

### 2.5 Threat model document

`docs/guides/threat-model.md` in the site nav: assets; trust boundaries
(codec / provider / KMS / consumer datastore); attacker capabilities considered
(ciphertext read, ciphertext write, KMS-permitted-but-untrusted KEK, log
reader, VM snapshot); what is defended and how; what is explicitly not
defended and the consumer-side mitigation (ciphertext substitution without
per-record AAD; timing; nonce reuse under cloned RNG state with the bounded
strategy; provider correctness); assurance status (KATs, fuzzing, mutation,
static analysis, review history); and an independent-review checklist for a
human cryptographer (the items an outside reviewer should verify: wire-format
AAD span, admission-before-unwrap, uniform failure, bounds, provider contract).

## Definition of done

- [ ] All Part 1 seams implemented with tests; spec 005 amended (1.1, 1.2, 1.3)
- [ ] KATs pass against embedded NIST and RFC vectors
- [ ] Fuzz regression runs in the suite; live fuzzing ran ≥ 2 minutes with no
      crash (or crashes fixed and corpus extended)
- [ ] PIT ≥ 85% mutation / ≥ 90% line in ci profile
- [ ] SpotBugs+findsecbugs clean at threshold Low, no filter file
- [ ] Threat model page builds strict; encryption.md documents both seams
- [ ] `./mvnw -Pci -B clean verify` green; codec-crypto compile surface still
      exactly codec-core; no ci allowlist changes
- [ ] CHANGELOG updated
