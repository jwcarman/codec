# Evolving Stored Formats

Stored bytes outlive the code that wrote them. A cache entry, a queue message,
a database column — all sit on disk or in memory long after the process that
wrote them has been redeployed, sometimes several times over. Bare codec
output carries no clue about which codec produced it: bytes written by Jackson
look exactly like bytes written by Fory, and a payload compressed with gzip
looks nothing like one compressed with zstd. Change the backend, or the
compression, or add encryption, and every payload already stored becomes
unreadable the moment the new code deploys.

`codec-versioned` fixes that by writing a version ahead of the payload and
dispatching decoding on it, so an application can change its storage strategy
without a flag day.

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-versioned</artifactId>
</dependency>
```

The [Spring Boot starter](spring-boot.md#the-starter) includes it; without the
starter, add it next to `codec-core`.

## The header, and what it costs

`VersionedCodec` prefixes every payload with three bytes: two magic bytes
(`0xC0 0xDC`) and an unsigned version number in `1..255`. The magic is not a
legacy-data discriminator — it exists so a codec pointed at bytes it did not
write fails cleanly instead of dispatching on garbage and decoding something
plausible but wrong.

Three bytes is the entire cost. No length prefix, no varint — the payload runs
to the end of the buffer, and `decode` hands the remainder to whichever codec
is registered for the version it read.

## First use: wrap today's codec as version 1

Even with only one format in play, registering it buys you the option to add
a second one later without a flag day:

```java
Codec<Person> codec = VersionedCodec.<Person>builder()
        .version(1, jacksonFactory.create(Person.class))
        .writing(1)
        .build();
```

`encode` writes `[0xC0][0xDC][0x01]` followed by the JSON. `decode` checks the
magic, reads the version byte, and delegates to the codec registered at that
version. `writing(1)` is required even with a single version registered —
there is no "highest wins" default.

## The upgrade: adding version 2

Say `Person` gains a field, and you take the opportunity to switch from JSON to
Fory with zstd compression. Register the new codec at version 2, keep version 1
registered so old data stays readable, and move `writing` to the version you
want new writes to use:

```java
Codec<Person> codec = VersionedCodec.<Person>builder()
        .version(1, jacksonFactory.create(Person.class))
        .version(2, foryFactory.create(Person.class).andThen(new ZstdCodec()))
        .writing(2)
        .build();
```

`decode` reads either version transparently: a version-1 payload from before
the upgrade and a version-2 payload written after it both come back as the
same `Person`. Nothing already stored needs rewriting.

## Rolling out a fleet

`writing` is explicit rather than inferred from the highest registered version,
and that is what makes a two-phase rollout possible:

1. Deploy every instance with version 2 **registered** but still
   `.writing(1)`. Every instance can now read version 2, but none writes it
   yet.
2. Once that deploy has reached the whole fleet, deploy again with
   `.writing(2)`. Only now does any instance start writing version 2, and by
   this point every reader already understands it.

Skip step 1 and an instance that has been upgraded starts writing version 2
before its peers know how to read it. Those peers see a valid header naming a
version they have no registration for and throw `UnknownVersionException` — an
`UnsupportedFormatException`, carrying the offending version. That is the
signal to **hold the record or route it to a newer reader, never to
quarantine it**. The data is fine; it was written by a newer deploy. A
dead-letter policy that treats `UnsupportedFormatException` the way it treats
`InvalidPayloadException` will discard messages from the instances that have
already been upgraded. See [Handling Failures](error-handling.md) for how the
two families divide the rest of the exception hierarchy.

## Retiring a version

Drop a version's registration once nothing in storage can still be at that
version — every record has aged out of the cache, every message has aged out
of the queue's retention, or a one-time migration has rewritten the store.
Removing the registration before that point turns the next read of a surviving
old record into an `UnknownVersionException` with nowhere to route it.

## What belongs inside the versioned codec, and what stays outside

Anything layered *outside* `VersionedCodec` has to be undone before the header
can even be read, so it is frozen for the life of the store:

```java
// header outermost: backend, transforms, everything inside may differ per version
Codec<Person> versioned = VersionedCodec.<Person>builder()
        .version(1, jackson.create(Person.class).andThen(new GzipCodec()))
        .version(2, fory.create(Person.class).andThen(new ZstdCodec()))
        .writing(2)
        .build();

// header now inside gzip: gzip must be undone to read it, so gzip can never change
Codec<Person> frozen = versioned.andThen(new GzipCodec());
```

The second form is legitimate when the outer layer carries its own
versioning — `EnvelopeCodec` does, with its own magic and version byte — but
it must be a deliberate choice, not an accident of chain order. The rule: put
whatever you might want to change inside the versioned codec; put only
permanent commitments — a KMS you are never migrating off of, a wire format
mandated by another system — outside it.

Because the builder is generic over `Codec<T>`, versioning a transform rather
than a whole value codec is just the `T = byte[]` case:

```java
Codec<byte[]> compression = VersionedCodec.<byte[]>builder()
        .version(1, new GzipCodec())
        .version(2, new ZstdCodec())
        .writing(2)
        .build();

Codec<Person> codec = factory.create(Person.class).andThen(compression);
```

## A payload without the header

`decode` throws `VersionedFormatException` — an `InvalidPayloadException` —
when the buffer is shorter than three bytes or the magic does not match. That
is the "these bytes were never ours" failure: a codec pointed at data some
other codec wrote, or genuinely corrupt input. Quarantine it. It shares no
parent below `CodecException` with `UnknownVersionException`, so a policy that
dead-letters malformed payloads cannot be tricked into discarding a newer
writer's output.

## Where next

- [Codec Composition](composition.md) — `andThen`, `xmap`, and the rest of the
  transforms `VersionedCodec` composes with
- [Handling Failures](error-handling.md) — the four exception families,
  and why `UnsupportedFormatException` and `InvalidPayloadException` demand
  opposite responses
- [Apache Fory](fory.md) — compatible mode tolerates added and removed fields
  on its own; put a `VersionedCodec` in front of it for changes compatible
  mode cannot absorb
