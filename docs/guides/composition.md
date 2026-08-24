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

## Built-in compression

Two transforms ship in `codec-core`:

| Transform | Format | When to use |
|-----------|--------|-------------|
| `GzipCodec` | gzip (RFC 1952) | Interoperating with external gzip tooling |
| `DeflateCodec` | zlib (RFC 1950) | High volumes of small payloads — ~12 bytes less framing |

`DeflateCodec` also exposes the compression level:

```java
new DeflateCodec(Deflater.BEST_COMPRESSION, maxDecodedSize);
```

### Decompression-bomb protection

Both transforms refuse to decode payloads that expand beyond a cap — 64 MiB by
default — throwing `IllegalStateException` instead of exhausting memory on
hostile input. Pass a byte limit to the constructor to tune it:

```java
new GzipCodec(1024 * 1024);  // refuse anything expanding past 1 MiB
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
