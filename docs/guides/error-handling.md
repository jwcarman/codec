# Handling failures

Every failure a codec reports from `encode` or `decode` is a `CodecException`,
and more precisely one of four families. The families are not organised by
*what went wrong* or by *which method you called* — you already know which
method you called. They are organised by **what you do next**, so that each
decision a consumer can make is one `catch` clause:

```java
try {
    return codec.decode(bytes);
} catch (InvalidPayloadException e) {
    // The bytes are bad — corrupt, truncated, tampered with, or not ours.
    // Quarantine: dead-letter the record, treat the cache entry as a miss.
} catch (UnsupportedFormatException e) {
    // The bytes are fine; this reader is too old for them. Hold the record,
    // route it to a newer reader, or upgrade. Never quarantine.
} catch (TransientCodecException e) {
    // Something the codec depends on failed — a key service, a JCE provider.
    // The input is not at fault. Retry with backoff, or alert on infrastructure.
}
```

On the encode side there is one more:

```java
try {
    return codec.encode(value);
} catch (InvalidValueException e) {
    // This value cannot be encoded by this codec: a type the backend has no
    // serializer for, a cyclic graph, an unregistered class. Fix the value or
    // the configuration; retrying will not help.
} catch (TransientCodecException e) {
    // As above.
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
and all preserve the underlying library's exception as the cause — the Jackson,
Gson, Fory or JCE detail is one `getCause()` away, but you never have to import
a library type to handle a codec failure.

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
are ahead of you. That is why `codec-versioned` reports an unregistered version
as `UnsupportedFormatException`, why `codec-crypto` reports an unknown format
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

The adapters propagate too. `codec-kafka`'s deserializer and
`codec-spring-data-redis`'s serializer let `CodecException` through unchanged;
Kafka itself wraps any deserializer failure in `RecordDeserializationException`,
and Spring's cache layer handles `RuntimeException`.

## Adding to the hierarchy

One rule: **subclass by what the caller does next.** A new failure whose answer
is one of the four above is a subclass of that family, never a fifth sibling; a
more specific subclass earns its existence only when it carries information the
family does not.
