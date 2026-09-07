# Spec 010 — `PowerOfTwoCodec`: alphabet-driven 2^k encodings, strict by default

Date: 2026-09-06
Status: approved scope; supersedes `Base32Codec`'s implementation, keeps its API

## Purpose

`Base32Codec` is a hand-written bit-packer for one alphabet family, documented as
"strict" while accepting two non-canonical forms (lowercase symbols, non-zero
trailing bits), and running at roughly a quarter of the speed the same loop can
reach. The JDK has Base64 and hex; it has no Base32, and no way to use any other
alphabet — Crockford (ULIDs), z-base-32, geohash, bcrypt's Base64.

This spec replaces the implementation with one alphabet-driven codec for every
power-of-two base, RFC-compliant and canonical by default with leniency as an
explicit opt-in, and specialised where it matters. `Base32Codec`'s public API is
preserved as presets. Findings closed: Fable repo-review M-9.

## What the spike established (2026-09-06, Apple M4 Max, 8 KB payload)

| k=5 | encode | decode |
|---|---:|---:|
| `Base32Codec` today | 506 MB/s | 1,127 MB/s |
| generic accumulator loop, any k | 980 MB/s | 1,249 MB/s |
| k=5 specialised shifts, alphabet as instance data | 1,650 MB/s | 2,460 MB/s |
| JDK `Base64` (k=6, intrinsic) | 14,400 MB/s | 12,100 MB/s |

- The speed lives in the shift constants, not the alphabet: the k=5 path runs
  identically for RFC, Crockford and geohash alphabets.
- The generic loop is correct for k=1..7 (287 round trips, padded and unpadded,
  byte-identical to the JDK for k=4 and k=6) but variable shift counts defeat
  the JIT's unrolling; it is 6–13× slower than the JDK for k=4/k=6.
- Therefore: one class, a k=5 fast path, the generic loop for other k, and
  `Base64Codec`/`HexCodec` untouched on the JDK's implementations.

## API

Package `org.jwcarman.codec.transform.encoding`, module `codec-transforms`.

```java
public final class PowerOfTwoCodec implements Codec<byte[]> {

  /** Strict codec over the alphabet, no padding. */
  public static PowerOfTwoCodec of(String alphabet);

  /** Strict codec over the alphabet, padding to a whole group with {@code pad}. */
  public static PowerOfTwoCodec of(String alphabet, char pad);

  /** Same alphabet and padding; decode folds symbol case. Encoding is unchanged. */
  public PowerOfTwoCodec caseInsensitive();

  /** Same codec; decode also maps each key in {@code aliases} to the alphabet symbol it names. */
  public PowerOfTwoCodec aliasing(Map<Character, Character> aliases);

  public int bitsPerSymbol();          // k
  public String alphabet();

  @Override public byte[] encode(byte[] value);
  @Override public byte[] decode(byte[] bytes);
}
```

Construction rules (all `IllegalArgumentException`):

- alphabet length is 2^k with 1 ≤ k ≤ 7; every symbol is ASCII (< 128) and
  distinct; the pad character, when given, is not in the alphabet.
- `caseInsensitive()` requires that no two alphabet symbols differ only by case
  (`aA` would be ambiguous). `aliasing(...)` requires each key to be ASCII, not
  in the alphabet, and each value to be in it.

`Base32Codec` keeps its public surface and becomes presets:

```java
public final class Base32Codec {                       // no longer a Codec itself
  public static PowerOfTwoCodec standard();            // RFC 4648 §6, '=' padding, strict
  public static PowerOfTwoCodec hex();                 // RFC 4648 §7 base32hex, '=' padding, strict
  public static PowerOfTwoCodec crockford();           // see below
}
```

Callers holding a `Base32Codec` type today hold a `Codec<byte[]>`; the factory
methods return `PowerOfTwoCodec`, which is a `Codec<byte[]>`, so `andThen(...)`
chains compile unchanged. A field typed `Base32Codec` is the one thing that
breaks; the CHANGELOG says so.

### Presets

| Preset | Alphabet | Pad | Decode policy |
|---|---|---|---|
| `Base32Codec.standard()` | `A–Z2–7` | `=` | strict, uppercase only |
| `Base32Codec.hex()` | `0–9A–V` | `=` | strict, uppercase only |
| `Base32Codec.crockford()` | `0123456789ABCDEFGHJKMNPQRSTVWXYZ` | none | case-insensitive; `I`,`L`→`1`, `O`→`0` (Crockford's own rules; the alphabet defines no canonical form, so leniency is the specification, not a relaxation). Hyphens are not accepted; Crockford's optional check symbol is not implemented. |

Case-insensitivity for the RFC presets is the caller's opt-in:
`Base32Codec.standard().caseInsensitive()`.

## Encoding

Standard bit-stream packing (RFC 4648 §3): input bytes are read most-significant
bit first; each k bits become one symbol; the final partial symbol is
left-aligned with zero bits; with padding, the output is padded to a whole group
of lcm(8, k)/k symbols. Output is the alphabet's symbols exactly (case as given).
Byte-identical to today's `Base32Codec`, to `java.util.Base64` for the Base64
alphabet, and to `HexFormat` for hex — pinned by tests.

## Decoding — strict means canonical

`decode` accepts exactly the strings `encode` can produce, and rejects, as
`InvalidPayloadException`:

1. a symbol outside the alphabet (after case folding / aliasing, when enabled);
2. with padding: a length that is not a multiple of the group size, a pad count
   that does not correspond to a whole number of input bytes, or a pad symbol
   anywhere but the tail;
3. without padding: a tail length that does not correspond to a whole number of
   input bytes (e.g. one Base32 symbol on its own);
4. **non-zero trailing bits** in the final symbol — `MZ======` is rejected;
   `MY======` decodes to `f`. RFC 4648 §3.5 permits either behaviour; §12
   explains why rejecting is the safe one: a lenient decoder gives one value
   many encodings, which defeats comparison, deduplication and signing of the
   encoded form.

Messages name the rule broken and, for a bad symbol, the symbol; never the
payload.

## Performance design

- k=5 has a specialised path: the 5-byte → 8-symbol group with literal shifts,
  alphabet and lookup table as instance fields. This is the path every
  Base32-family alphabet takes.
- Every other k uses the generic accumulator loop: lcm(8, k)/8 bytes in, lcm(8,
  k)/k symbols out, one group per iteration, a `long` accumulator (max 56 bits
  for k=7).
- Both paths write into a pre-sized `byte[]`; no `StringBuilder`, `String`, or
  `ByteArrayOutputStream` anywhere.
- Dispatch is one branch at construction. No reflection, no code generation.
- Not in scope: a wide-load (SWAR/VarHandle) variant, the Vector API, or
  specialised paths for k=4/k=6 — the JDK owns those.

## Behaviour changes (CHANGELOG, Breaking)

- `Base32Codec.standard()` and `.hex()` decode uppercase only and reject
  non-zero trailing bits. Migration for callers who fed lowercase:
  `.caseInsensitive()`.
- `Base32Codec` is no longer itself a `Codec<byte[]>`; its factories return
  `PowerOfTwoCodec`. Fields typed `Base32Codec` become `Codec<byte[]>` (or
  `PowerOfTwoCodec`).
- `Base64Codec` is unchanged and remains as lenient on trailing bits as
  `java.util.Base64`, which has no strict mode; the composition guide states the
  asymmetry.

## Documentation

- `docs/guides/composition.md`, "Text-safe output": `PowerOfTwoCodec.of(...)`
  with the Crockford/ULID example, the strict-by-default rule and the
  `caseInsensitive()` opt-in, the Base64 asymmetry sentence, and the guidance
  that Base64 and hex stay on `Base64Codec`/`HexCodec`.
- `docs/benchmarks.md`: fresh Base32 rows from a re-run of `EncodingBenchmark`,
  and the "slowest of them" sentence replaced by what is.
- `error-handling.md`'s module table: the Base32/Base64/hex row names
  `PowerOfTwoCodec` alongside.

## Testing

- Construction: every rule above, each as its own `IllegalArgumentException`
  test.
- Round trips for k=1..7, lengths 0..40, padded and unpadded (the spike's
  matrix, committed).
- Byte-identity: k=5 against the RFC 4648 §10 vectors (already in
  `Base32CodecTest`), k=6 against `java.util.Base64`, k=4 against `HexFormat`,
  on random inputs of lengths 0..40.
- Strictness: the four rejection classes, for the k=5 path and for a generic-k
  alphabet; `MZ======`/`MY======` explicitly; lowercase rejected by default and
  accepted with `caseInsensitive()`; Crockford folds `I/L/O` and case and
  rejects `U`.
- The k=5 path and the generic path agree: a `PowerOfTwoCodec` for the RFC
  alphabet constructed to force the generic loop (test-only hook, or a
  parameterised alphabet of k=5 fed through the generic path) produces identical
  output and identical rejections on the full matrix.
- Existing `Base32CodecTest` cases stay green except the two that asserted
  leniency, which flip to assert rejection.

## Definition of done

- `PowerOfTwoCodec` shipped with the API above, k=5 fast path, generic loop,
  full Javadoc; `Base32Codec` reduced to three presets.
- `./mvnw -Pci -B clean verify` green; the module stays dependent on
  `codec-core` only.
- Benchmarks re-run for the encoding rows; docs and CHANGELOG updated as above.
- Fable repo-review M-9 closed.
