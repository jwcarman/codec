# Codec Composition

Any `Codec<byte[]>` is a byte-level transform: `encode` is the forward direction
(compress, encrypt), `decode` its inverse. `Codec.andThen` layers such a
transform onto any codec:

```java
Codec<Person> codec =
    codecFactory.create(Person.class).andThen(new GzipCodec());
```

Encoding runs the base codec first, then the transform. Decoding inverts the
transform first, then the base codec — so composition is always symmetric.

## Chaining

Transforms chain left to right on encode and unwind automatically in reverse
order on decode:

```java
Codec<Person> codec =
    codecFactory.create(Person.class)
        .andThen(new GzipCodec())      // compress first...
        .andThen(new AesCodec(key));   // ...then encrypt
```

Here `encode` produces JSON → gzip → ciphertext, and `decode` transparently
runs decrypt → gunzip → JSON.

!!! tip "Compress before you encrypt"
    Ciphertext does not compress, so put compression before encryption in the
    chain.

## The transforms module

The zero-dependency transforms live in `codec-transforms` (the starter includes
it; without Spring, add it next to `codec-core`):

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-transforms</artifactId>
</dependency>
```

Four packages, by kind: `transform.compress`, `transform.encoding`,
`transform.checksum`, and `transform.text` (all under `org.jwcarman.codec`).

## Choosing a transform

Three questions pick a transform: do you need the payload smaller, do you need
it text-safe, and who else has to read it?

| You need | Reach for | Because |
|----------|-----------|---------|
| Smaller payloads, no strong preference | `ZstdCodec` | Best ratio-for-speed of any option; the modern default for caches and queues |
| The fastest possible compression | `Lz4Codec` | Compresses and decompresses at memory-bandwidth rates; ratio is the trade |
| A better LZ4 ratio, same decode speed | `Lz4Codec.highCompression()` | LZ4-HC compresses slower but decodes just as fast — good when writes are rare and reads are hot |
| Interop with gzip tooling, HTTP, or files | `GzipCodec` | Everything reads gzip; the format carries a CRC |
| Many small payloads, no native deps | `DeflateCodec` | Same algorithm as gzip minus ~12 bytes of framing; JDK-native |
| Bytes in a text column, JSON string, or header | `Base64Codec.basic()` | The universal text encoding; 33% overhead |
| Bytes in a URL, filename, or token | `Base64Codec.urlSafe()` / `urlSafeWithoutPadding()` | No `/`, `+`, or `=` to escape |
| A value a person will type or read aloud | `Base32Codec.standard()` | No lower case, no symbols, and no `0`/`1`/`8`/`9` — the confusable digits; the encoding used for TOTP secrets |
| A text form that sorts like the bytes | `Base32Codec.hex()` | base32hex preserves byte order under lexicographic sort |
| A value for logs, diagnostics, or checksums | `HexCodec` | Twice the size, but instantly recognisable and copy-pasteable |
| Corruption detection on an uncompressed, unencrypted payload | `ChecksumCodec.crc32c()` | Four bytes; rejects bit rot and truncation before a parser sees them. Compressed frames and encrypted payloads already have this |
| The bytes *are* the text | `StringCodec.utf8()` | Raw text, not a JSON string; strict decoding; a backend-free base for `xmap` |

The compression rows all carry the decompression-bomb cap described below.
The `codec-transforms` transforms are pure JDK; `ZstdCodec` and `Lz4Codec`
live in `codec-zstd` and `codec-lz4` because each needs a library with native
code. Combine freely — compress first, encode last, and put
[encryption](encryption.md) in between.

## Built-in compression

Two transforms ship in `codec-transforms`; the ones that need a dependency get their own module:

| Transform | Module | Format | When to use |
|-----------|--------|--------|-------------|
| `GzipCodec` | `codec-transforms` | gzip (RFC 1952) | Interoperating with external gzip tooling |
| `DeflateCodec` | `codec-transforms` | zlib (RFC 1950) | High volumes of small payloads — ~12 bytes less framing |
| `ZstdCodec` | `codec-zstd` | Zstandard (RFC 8878) | The default choice for caches and queues: faster than gzip at every level and usually smaller |
| `Lz4Codec` | `codec-lz4` | LZ4 frame | When speed is everything: hot caches and high-volume streams; also interoperates with Kafka and the `lz4` CLI |

`ZstdCodec` lives in `codec-zstd` because it is backed by `zstd-jni`, which
bundles native libraries for the common platforms. Its level is configurable:

```java
new ZstdCodec();          // the library's default level
new ZstdCodec(19);        // smaller and slower
```

`DeflateCodec` also exposes the compression level:

```java
new DeflateCodec(Deflater.BEST_COMPRESSION, maxDecodedSize);
```

### Decompression-bomb protection

All three transforms refuse to decode payloads that expand beyond a cap — 64 MiB
by default — throwing `IllegalStateException` instead of exhausting memory on
hostile input. Pass a byte limit to the constructor to tune it:

```java
new GzipCodec(1024 * 1024);  // refuse anything expanding past 1 MiB
```

## Text-safe output

`Base64Codec` turns any bytes into ASCII-only bytes, for chains whose output
must live somewhere that mangles raw bytes — a text column, a JSON string
field, a URL. Put it **last**: it expands by a third, so compress and encrypt
first.

```java
Codec<Order> codec = codecFactory.create(Order.class)
    .andThen(new GzipCodec())
    .andThen(Base64Codec.urlSafeWithoutPadding());

String token = new String(codec.encode(order), StandardCharsets.US_ASCII);
```

Four variants: `basic()`, `urlSafe()`, `urlSafeWithoutPadding()` (the usual
choice for tokens and query strings), and `mime()`. Decoding is strict —
input outside the variant's alphabet is rejected rather than decoded to
garbage.

`HexCodec` (RFC 4648 base16) is the readable alternative: twice the size of
the input, but a person can read and paste it — identifiers, checksums, keys
in configuration. `lowerCase()` is the usual form; `upperCase()` matches the
RFC's examples; decoding accepts either and rejects odd-length or non-hex
input.

`Lz4Codec` (in `codec-lz4`, backed by `lz4-java`) writes the standard LZ4
frame format. The default compressor is the fast one; `Lz4Codec.highCompression()`
selects LZ4-HC for a better ratio at a slower compression speed — decompression
is equally fast either way:

```java
new Lz4Codec();                           // fast compressor
Lz4Codec.highCompression();               // LZ4-HC
new Lz4Codec(16L * 1024 * 1024);          // fast, with a 16 MiB decoded cap
```

`Base32Codec` covers the rest of RFC 4648: `standard()` is the `A-Z2-7`
alphabet TOTP secrets and DNS-safe identifiers use — no lower case, no
symbols, so it survives case-insensitive contexts; `hex()` is base32hex,
whose encoded form sorts in the same order as the bytes it encodes. Output
is upper-case and padded; decoding is case-insensitive and rejects bad
lengths, padding, or characters.

## Corruption detection

`ChecksumCodec` appends a 32-bit checksum on encode and verifies it on decode,
rejecting a mismatch with `IllegalArgumentException`. It catches accidental
damage — bit rot, a truncated write, a partially overwritten cache entry — so
corrupt bytes fail here rather than confusing a parser or decoding to a
plausible but wrong value:

```java
Codec<Person> codec =
    factory.create(Person.class).andThen(ChecksumCodec.crc32c());
```

It is **not** tamper-proof — anyone who can change the bytes can recompute the
checksum. Encrypted payloads from [`codec-crypto`](encryption.md) are already
authenticated, and gzip, zstd, and LZ4 frames carry their own checksums, so the
realistic use is a plain, uncompressed payload such as JSON in a cache.
`crc32c()` is the recommended form; any other 32-bit `java.util.zip.Checksum`
(`CRC32`, `Adler32`) can be supplied for interoperability.

## Text as bytes

A backend's `create(String.class)` gives you a *JSON string*: `hello` becomes
the seven bytes `"hello"`. `StringCodec` is the codec for text that should be
its own bytes:

```java
Codec<String> text = StringCodec.utf8();          // or StringCodec.of(charset)
```

Decoding is strict — malformed input is rejected rather than silently replaced
with U+FFFD. It is also the natural base for deriving codecs with `xmap`.

## Deriving codecs with `xmap`

Where `andThen` wraps the *bytes* side of a codec, `Codec.xmap` wraps the
*value* side: given a conversion in each direction, it derives a codec for
another type. This is the tool for the domain-type-versus-wire-type split — a
backend that only serializes generated or registered classes, wrapped as the
type the application actually uses:

```java
Codec<Person> codec =
    protobufFactory.create(PersonProto.class).xmap(Person::fromProto, Person::toProto);

Codec<UUID> ids = StringCodec.utf8().xmap(UUID::fromString, UUID::toString);
```

Exceptions from either conversion propagate unchanged, and the result composes
with `andThen` like any other codec. For the JSON backends, which already
serialize records and value types directly, you rarely need it.

## Null handling

Whether a codec accepts `null` is its own business: a JSON backend encodes it
as the literal `null`, the transforms reject it. When an integration's contract
treats `null` as "absent" — a cache serializer handed `null` on a miss —
`nullSafe()` makes that explicit: `null` passes straight through in both
directions and everything else is delegated.

```java
Codec<Person> codec = factory.create(Person.class).andThen(new ZstdCodec()).nullSafe();
```

## Custom transforms

For stream-based compression libraries (zstd, lz4, xz, ...), extend
`CompressionStreamCodec` — two one-line overrides, and the buffering and
decompression cap come for free:

```java
public class ZstdCodec extends CompressionStreamCodec {

    public ZstdCodec() {
        super(64L * 1024 * 1024);
    }

    @Override
    protected OutputStream compressing(OutputStream sink) throws IOException {
        return new ZstdOutputStream(sink);
    }

    @Override
    protected InputStream decompressing(InputStream source) throws IOException {
        return new ZstdInputStream(source);
    }
}
```

For anything else — encryption, encoding, checksumming — implement
`Codec<byte[]>` directly. Keep `encode`/`decode` symmetric and thread-safe, and
it composes like everything else.
