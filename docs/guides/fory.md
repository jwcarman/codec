# Apache Fory

`codec-fory` is the backend for JVM-to-JVM payloads where speed and size matter —
cache entries, queue messages, journal records. It serializes through
[Apache Fory](https://fory.apache.org), which is typically several times faster
than Kryo and, on anything beyond a handful of fields, produces smaller output
than JSON.

```java
CodecFactory factory = ForyCodecFactory.of(Person.class, Order.class);

Codec<Order> codec = factory.create(Order.class);
```

## Registration is mandatory

Fory refuses to serialize or deserialize any class that has not been
registered. That rule is what closes the deserialization-gadget class of
attacks, and `codec-fory` never relaxes it: `ForyCodecFactory.of(...)` builds
Fory in Java mode with `requireClassRegistration(true)` and registers the
classes you pass. Register every type a codec will carry, including the element
types of collections — the JDK's own collections and boxed types are already
registered by Fory.

The helper also applies the one safeguard from Fory's
[security guidance](https://fory.apache.org/docs/object-serialization/java/security/)
that Fory does not switch on by default in compatible mode:
`withDeserializeUnknownClass(false)`. Without it a payload naming a class the
instance has not registered is materialised from its metadata as an anonymous
struct; with it the payload is rejected. Fory's other safeguards — a read depth
of 50, a 128 MiB graph-memory gate, bounds on container and metadata sizes —
are already its defaults and are left as they are.

!!! danger "Do not disable registration for convenience"
    A `Fory` built with `requireClassRegistration(false)` will deserialize any
    class on the classpath that the bytes name. If you hand such an instance
    to `new ForyCodecFactory(fory)`, the factory does not second-guess you —
    the responsibility is yours.

## Creation fails fast

`create(...)` checks the requested type — and every class named in its type
arguments — against the instance's registrations and throws
`IllegalArgumentException` at creation if one is missing, rather than letting
the first `encode` fail later in production. `List<Person>` with an
unregistered `Person` is caught; so is a bare `Unregistered.class`. JDK types,
interfaces, and abstract classes are exempt: Fory registers the JDK's concrete
types itself and decides interfaces by each value's runtime class.

To probe before creating, `factory.supports(SomeClass.class)` answers the same
question as a boolean.

## Schema evolution

`ForyCodecFactory.of(...)` is a helper: beyond requiring registration it takes
Fory's defaults, and the wire format follows them. Since Fory 1.2.0 the default
is **compatible mode**: every payload carries its class schema, so a reader
whose class has gained a field since the payload was written sees `null` there,
and one whose class has lost a field simply skips it. The alternative,
schema-consistent mode, does not fail on that drift — it returns a *wrong
object*, with the remaining values shifted into the wrong fields — which is why
Fory changed its default.

The cost is a few bytes of metadata per class per message. On a real payload it
is noise (the 100-item order in the [benchmarks](../benchmarks.md) grows 2%),
but a lone four-field record roughly doubles and ends up no smaller than its
JSON.

Two things follow from inheriting the default. The two formats are not
symmetric — a compatible-mode reader reads both, but a schema-consistent reader
cannot read compatible-mode bytes — and a future Fory release could change the
default again. If either matters to you, build your own `ThreadSafeFory` with
the mode named explicitly and hand it to the constructor below; and put a
[`VersionedCodec`](composition.md#versioning-the-format) in front of it before
you ever need to change your mind.

## Bring your own Fory

`new ForyCodecFactory(ThreadSafeFory)` accepts a caller-configured instance for
anything beyond the default — schema-consistent mode for the smallest output,
custom serializers, or a shared instance. Codecs must be thread-safe and a plain
`Fory` is not, so the constructor takes only a `ThreadSafeFory`; build one
with `Fory.builder()...buildThreadSafeFory()`.

## When not to use it

The wire format is Fory's own and JVM-specific:

- Another language will read the bytes — use CBOR, JSON, or Protocol Buffers.
- The data must outlive the classes that wrote it — compatible mode tolerates
  added and removed fields, not renamed or retyped ones; use a schema-based
  format, and test the evolution you expect.

## Spring Boot

There is no default Fory the starter could build for you — it needs your
classes registered — so the auto-configuration is triggered by a bean: define a
`ThreadSafeFory` bean and `codec-autoconfigure` registers a `ForyCodecFactory`
from it, ahead of every classpath-detected backend.

```java
@Bean
ThreadSafeFory fory() {
    ThreadSafeFory fory = Fory.builder()
        .withLanguage(Language.JAVA)
        .requireClassRegistration(true)
        .buildThreadSafeFory();
    fory.register(Person.class);
    fory.register(Order.class);
    return fory;
}
```

## Where next

- [Getting Started](getting-started.md) — dependencies and first codec
- [Codec Composition](composition.md) — add compression or encryption on top
