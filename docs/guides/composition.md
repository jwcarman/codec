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

Two packages, by kind: `org.jwcarman.codec.transform.compress` and
`org.jwcarman.codec.transform.encoding`.

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
