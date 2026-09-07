# Spec 010 — `Base32Codec`: strict by default, alphabet-driven, optimised

Date: 2026-09-06 (revised the same day: Base32 only, no generic power-of-two codec)
Status: approved scope; replaces `Base32Codec`'s implementation, keeps its type

## Purpose

`Base32Codec` is a hand-written bit-packer documented as "strict" while accepting
two non-canonical forms (lowercase symbols, non-zero trailing bits), and running
at roughly a third of the speed the same loop can reach. It also hard-codes two
alphabets, while the Base32 family in the wild includes Crockford (ULIDs),
z-base-32 and geohash — all 32-symbol alphabets, all the same bit layout.

This spec makes `Base32Codec` RFC-compliant and canonical by default with
leniency as an explicit opt-in, lets it take any 32-symbol alphabet, and
specialises its loops. The type stays a `Codec<byte[]>`; the two existing
factories keep their names. Findings closed: Fable repo-review M-9.

## What the spike established (2026-09-06, Apple M4 Max, 8 KB payload)

| k=5 | encode | decode |
|---|---:|---:|
| `Base32Codec` today | 506 MB/s | 1,127 MB/s |
| generic accumulator loop, any k | 980 MB/s | 1,249 MB/s |
| k=5 specialised shifts, alphabet as instance data | 1,650 MB/s | 2,460 MB/s |
| JDK `Base64` (k=6, intrinsic) | 14,400 MB/s | 12,100 MB/s |

- The speed lives in the shift constants, not the alphabet: the specialised loop
  runs identically for RFC, Crockford and geohash alphabets.
- A generic loop for other symbol widths was measured and rejected: every
  alternative alphabet anyone uses is 32 symbols, and for Base64 and hex the JDK's
  intrinsics are 6–13× faster than any pure-Java loop. `Base64Codec` and
  `HexCodec` stay on the JDK.

## API

Package `org.jwcarman.codec.transform.encoding`, module `codec-transforms`.

```java
public final class Base32Codec implements Codec<byte[]> {

  /** RFC 4648 §6: A–Z2–7, '=' padding, strict. */
  public static Base32Codec standard();

  /** RFC 4648 §7 base32hex: 0–9A–V, '=' padding, strict. */
  public static Base32Codec hex();

  /** Crockford's alphabet, unpadded; decode folds case and reads I, L as 1 and O as 0. */
  public static Base32Codec crockford();

  /** z-base-32 (ybndrfg8ejkmcpqxot1uwisza345h769), unpadded, strict. */
  public static Base32Codec zBase32();

  /** The geohash alphabet (0-9b-z without a, i, l, o), unpadded, strict. */
  public static Base32Codec geohash();

  /** Strict codec over a 32-symbol alphabet, no padding. */
  public static Base32Codec of(String alphabet);

  /** Strict codec over a 32-symbol alphabet, padding to a whole group of 8 with {@code pad}. */
  public static Base32Codec of(String alphabet, char pad);

  /** Same alphabet and padding; decode folds symbol case. Encoding is unchanged. */
  public Base32Codec caseInsensitive();

  /** Same codec; decode also maps each key in {@code aliases} to the alphabet symbol it names. */
  public Base32Codec aliasing(Map<Character, Character> aliases);

  public String alphabet();

  @Override public byte[] encode(byte[] value);
  @Override public byte[] decode(byte[] bytes);
}
```

Construction rules (all `IllegalArgumentException`; a null alphabet or alias map
is `NullPointerException`):

- the alphabet has exactly 32 symbols; every symbol is ASCII (< 128) and
  distinct; the pad character, when given, is ASCII and not in the alphabet.
- `caseInsensitive()` requires that no two alphabet symbols differ only by case
  and that the pad symbol is not the other case of a symbol (`aA` would be
  ambiguous).
- `aliasing(...)` requires each key to be ASCII, not the pad symbol, and not
  already accepted (an alphabet symbol, a folded case, or an earlier alias); and
  each value to be an alphabet symbol. Aliases are exact characters: to accept
  both cases of an alias, list both.

Instances are immutable; `caseInsensitive()` and `aliasing(...)` return new
instances and leave the original strict.

### Presets

| Preset | Alphabet | Pad | Decode policy |
|---|---|---|---|
| `standard()` | `A–Z2–7` | `=` | strict, uppercase only |
| `hex()` | `0–9A–V` | `=` | strict, uppercase only |
| `crockford()` | `0123456789ABCDEFGHJKMNPQRSTVWXYZ` | none | case-insensitive; `I`,`L`→`1`, `O`→`0` (Crockford's own rules; the alphabet defines no canonical form, so leniency is the specification, not a relaxation). Hyphens are not accepted; Crockford's optional check symbol is not implemented. |
| `zBase32()` | `ybndrfg8ejkmcpqxot1uwisza345h769` | none | strict, lowercase only |
| `geohash()` | `0123456789bcdefghjkmnpqrstuvwxyz` | none | strict, lowercase only |

Case-insensitivity for the strict presets is the caller's opt-in:
`Base32Codec.standard().caseInsensitive()`, `Base32Codec.geohash().caseInsensitive()`.

## Encoding

RFC 4648 §3 bit-stream packing: input bytes are read most-significant bit first;
each 5 bits become one symbol; the final partial symbol is left-aligned with zero
bits; with padding, the output is padded to a whole group of 8 symbols. Output is
the alphabet's symbols exactly (case as given). Byte-identical to today's
`Base32Codec` for the two RFC alphabets — pinned by the RFC 4648 §10 vectors
already in `Base32CodecTest`.

## Decoding — strict means canonical

`decode` accepts exactly the strings `encode` can produce, and rejects, as
`InvalidPayloadException`:

1. a symbol outside the alphabet (after case folding / aliasing, when enabled);
2. with padding: a length that is not a multiple of 8, a pad count that does not
   correspond to a whole number of input bytes (1, 3, 4 or 6 pad symbols), or a
   pad symbol anywhere but the tail;
3. without padding: a tail length that does not correspond to a whole number of
   input bytes (1, 3 or 6 symbols after the last whole group);
4. **non-zero trailing bits** in the final symbol — `MZ======` is rejected;
   `MY======` decodes to `f`. RFC 4648 §3.5 permits either behaviour; §12
   explains why rejecting is the safe one: a lenient decoder gives one value many
   encodings, which defeats comparison, deduplication and signing of the encoded
   form.

Messages name the rule broken and, for a bad symbol, the symbol; never the
payload.

## Performance design

- Whole groups: the 5-byte → 8-symbol loop with literal shifts, alphabet and
  lookup table as instance fields — the spike's specialised loop.
- The tail (0–4 remaining bytes, 0–7 remaining symbols) is handled once, after the
  loop, by a small accumulator.
- Encode and decode write into a pre-sized `byte[]`; no `StringBuilder`,
  `String`, or `ByteArrayOutputStream`.
- No reflection, no code generation, one code path. Not in scope: a wide-load
  (SWAR/VarHandle) variant or the Vector API.

## Behaviour changes (CHANGELOG, Breaking)

- `Base32Codec.standard()` and `.hex()` decode uppercase only and reject non-zero
  trailing bits. Migration for callers who fed lowercase: `.caseInsensitive()`.
- `Base64Codec` is unchanged and remains as lenient on trailing bits as
  `java.util.Base64`, which has no strict mode; the composition guide states the
  asymmetry.

## Documentation

- `docs/guides/composition.md`, "Text-safe output": the strict-by-default rule
  and the `caseInsensitive()` opt-in, `crockford()` and `of(...)` with the
  ULID/z-base-32 example, the Base64 asymmetry sentence.
- `docs/benchmarks.md`: fresh Base32 rows from a re-run of `EncodingBenchmark`,
  and the "slowest of them" sentence replaced by what is.
- `error-handling.md`'s module table: the Base32 row names the canonical-form
  rejections.

## Testing

- Construction: every rule above, each as its own `IllegalArgumentException`
  test.
- RFC 4648 §10 vectors for both RFC alphabets (already in `Base32CodecTest`),
  encode and decode; Crockford, z-base-32 and geohash vectors derived from them.
- Round trips for lengths 0..41, padded and unpadded, plus one large payload.
- Strictness: the four rejection classes, `MZ======`/`MY======` explicitly, a pad
  symbol inside a group, a bad symbol inside a whole group and in the tail;
  lowercase rejected by default and accepted with `caseInsensitive()`; Crockford
  folds `I/L/O` and case and rejects `U`, hyphens and the check symbols.
- Existing `Base32CodecTest` cases stay green except the one that asserted
  leniency (`accepts_lower_case_input`), which flips to assert rejection.

## Definition of done

- `Base32Codec` shipped with the API above, specialised loops, full Javadoc.
- `./mvnw -Pci -B clean verify` green; the module stays dependent on
  `codec-core` only.
- Benchmarks re-run for the Base32 rows; docs and CHANGELOG updated as above.
- Fable repo-review M-9 closed.
