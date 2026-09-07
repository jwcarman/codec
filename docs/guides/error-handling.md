# Handling Failures

Every failure a codec reports from `encode` or `decode` is a `CodecException`,
and more precisely one of four families. The families are not organised by
*what went wrong* or by *which method you called* — you already know which
method you called. They are organised by **what you do next**, so that each
decision a consumer can make is one `catch` clause:

```java
Person decodeOrNull(byte[] bytes) {
    try {
        return codec.decode(bytes);
    } catch (InvalidPayloadException e) {
        // The bytes are bad — corrupt, truncated, tampered with, or not ours.
        // Quarantine: dead-letter the record, treat the cache entry as a miss.
        return null;
    } catch (UnsupportedFormatException e) {
        // The bytes are fine; this reader is too old for them. Hold the record,
        // route it to a newer reader, or upgrade. Never quarantine: let it
        // propagate so the caller can park the record instead of dropping it.
        throw e;
    } catch (TransientCodecException e) {
        // Something the codec depends on failed — a key service, a JCE provider.
        // The input is not at fault. Retry with backoff, or alert on infrastructure.
        throw e;
    }
}
```

All four classes live in `org.jwcarman.codec.spi`, in `codec-core` — the same
artifact that gives you `Codec` and `CodecFactory`, so
`import org.jwcarman.codec.spi.InvalidPayloadException;` (and its three
siblings) is all a consumer needs; no extra dependency is required just to
catch a codec failure.

On the encode side there is one more:

```java
byte[] encodeOrNull(Person value) {
    try {
        return codec.encode(value);
    } catch (InvalidValueException e) {
        // This value cannot be encoded by this codec: a type the backend has no
        // serializer for, a cyclic graph, an unregistered class. Fix the value or
        // the configuration; retrying will not help.
        return null;
    } catch (TransientCodecException e) {
        // As above.
        throw e;
    }
}
```

`catch (CodecException e)` is the coarse form — "something went wrong in the
codec" — for callers that do not distinguish.

## The four families

| Family | Meaning | Your response |
|---|---|---|
| `InvalidValueException` | the value handed to `encode` cannot be encoded | fix the value or the configuration |
| `InvalidPayloadException` | the payload handed to `decode` is malformed, corrupt, or forged | quarantine |
| `UnsupportedFormatException` | the payload is well-formed but this reader cannot handle it | hold, route, upgrade |
| `TransientCodecException` | something the codec depends on failed; the input is not at fault | retry, or alert |

All four are unchecked, all extend only `CodecException` and `RuntimeException`,
and preserve the underlying library's exception as the cause where there is
one — the Jackson, Gson, Fory or JCE detail is one `getCause()` away for most
throw sites, but not all: a bad magic number, a bounds check, or a checksum
mismatch is detected by this library's own code with no wrapped library
exception to report, so `getCause()` can be `null`. Either way, you never have
to import a library type to handle a codec failure.

Some modules add a more specific subclass where it carries information the
family does not: `codec-versioned`'s `UnknownVersionException` (an
`UnsupportedFormatException` that carries the version), and `codec-crypto`'s
`DecryptionException` (an `InvalidPayloadException` whose message is
deliberately uniform across cryptographic rejections), `KeyAccessException` and
`EncryptionException` (both `TransientCodecException`). You catch the family; the
subclass is there when you want to know more.

## Why the third family matters

`InvalidPayloadException` and `UnsupportedFormatException` look similar — in
both cases `decode` refused the bytes — but they demand opposite responses.
During a rolling upgrade, instances that have already been upgraded write
payloads the old instances cannot read. If those were reported as *invalid*, a
dead-letter policy would discard every one of them. They are not invalid; they
are ahead of you. That is why [`codec-versioned`](composition.md#versioning-the-format)
reports an unregistered version as `UnsupportedFormatException`, why
[`codec-crypto`](encryption.md#error-taxonomy) reports an unknown format
version or algorithm id the same way, and why the two families share no parent
below `CodecException`.

## What is not a codec failure

Construction and configuration errors are programmer errors at wiring time, not
runtime outcomes a pipeline reacts to, and they keep the JDK's types: a bad
builder argument is `IllegalArgumentException`, an inconsistent builder
`IllegalStateException`, a `null` anywhere — including `encode(null)` and
`decode(null)` on a bare codec — `NullPointerException`. `CodecFactory.create`
failing for a type it cannot handle is likewise `IllegalArgumentException`.

Composition passes failures through: `andThen`, `nullSafe` and `xmap` propagate
whatever the underlying codec throws. The functions you hand to `xmap` may throw
anything; that is yours.

The adapters propagate too. [`codec-kafka`](kafka.md)'s deserializer and
[`codec-spring-data-redis`](redis.md)'s serializer let `CodecException` through
unchanged, families and all — see [In practice](#in-practice) below for both.

## In practice

A Kafka consumer decoding records manually shows where each catch sits around
the codec call:

```java
for (ConsumerRecord<String, byte[]> record : records) {
    try {
        Order order = codec.decode(record.value());
        process(order);
    } catch (InvalidPayloadException e) {
        // Poison record: send it to a dead-letter topic and move on.
    } catch (UnsupportedFormatException e) {
        // A newer writer is ahead of this consumer: park the record, alert,
        // do not skip it.
    } catch (TransientCodecException e) {
        // Retry the same record with backoff.
    }
}
```

Dead-lettering a record, parking a partition, and retrying without losing an
offset are Kafka's concerns, not the codec's; see [Apache Kafka](kafka.md) for
how `codec-kafka`'s `Deserializer` plugs a codec into a consumer.

The cache case is the same shape: `codec-spring-data-redis`'s
`CodecRedisSerializer` lets any `CodecException` through unchanged, so a
`CacheErrorHandler` decides what happens next. Treating `InvalidPayloadException`
as a miss is reasonable — a corrupted entry is not the caller's fault — but a
`TransientCodecException` should propagate as an error, or a KMS outage looks
like an empty cache.

## What each module throws

| Module | Situation | Family (and subclass) |
|---|---|---|
| `codec-transforms` | gzip/deflate/zstd/lz4 decode a corrupt stream | `InvalidPayloadException` |
| `codec-transforms` | a payload that would expand past the decompression cap | `InvalidPayloadException` |
| `codec-transforms` | a compressor fails on encode (dependency, not the value) | `TransientCodecException` |
| `codec-transforms` | `ChecksumCodec` finds a mismatch or a truncated buffer | `InvalidPayloadException` |
| `codec-transforms` | Base64/Base32/hex text outside the alphabet | `InvalidPayloadException` |
| `codec-transforms` | `StringCodec` decodes bytes not valid in the charset | `InvalidPayloadException` |
| `codec-jackson`, `codec-jackson2`, `codec-gson`, `codec-jsonb` | malformed JSON on decode | `InvalidPayloadException` |
| `codec-jackson`, `codec-jackson2`, `codec-gson`, `codec-jsonb` | a value the backend cannot serialize on encode | `InvalidValueException` |
| `codec-protobuf` | invalid protobuf bytes | `InvalidPayloadException` |
| `codec-fory` | corrupt or truncated Fory bytes | `InvalidPayloadException` |
| `codec-fory` | a decoded value of the wrong type | `InvalidPayloadException` |
| `codec-fory` | an unregistered class on encode | `InvalidValueException` |
| `codec-versioned` | bad magic or a buffer shorter than the header | `InvalidPayloadException` (`VersionedFormatException`) |
| `codec-versioned` | a version this codec has no codec registered for | `UnsupportedFormatException` (`UnknownVersionException`) |
| `codec-crypto` | GCM tag mismatch, tampering, or a disallowed keyId | `InvalidPayloadException` (`DecryptionException`) |
| `codec-crypto` | an unknown format version or algorithm id | `UnsupportedFormatException` |
| `codec-crypto` | the KMS is unavailable, or a provider returns an unusable key | `TransientCodecException` (`KeyAccessException`) |
| `codec-crypto` | a provider or strategy failure on encode | `TransientCodecException` (`EncryptionException`) |

## Adding to the hierarchy

One rule: **subclass by what the caller does next.** A new failure whose answer
is one of the four above is a subclass of that family, never a fifth sibling; a
more specific subclass earns its existence only when it carries information the
family does not.
