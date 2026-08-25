# Apache Fory

`codec-fory` is the backend for JVM-to-JVM payloads where speed and size matter —
cache entries, queue messages, journal records. It serializes through
[Apache Fory](https://fory.apache.org), which is typically several times faster
than Kryo and produces smaller output than JSON.

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

## Bring your own Fory

`new ForyCodecFactory(ThreadSafeFory)` accepts a caller-configured instance for
anything beyond the default — compatible mode for schema evolution, custom
serializers, or a shared instance. Codecs must be thread-safe and a plain
`Fory` is not, so the constructor takes only a `ThreadSafeFory`; build one
with `Fory.builder()...buildThreadSafeFory()`.

## When not to use it

The wire format is Fory's own and JVM-specific:

- Another language will read the bytes — use CBOR, JSON, or Protocol Buffers.
- The data must outlive the classes that wrote it — use a schema-based format,
  or at minimum Fory's compatible mode, and test the evolution you expect.

## Where next

- [Getting Started](getting-started.md) — dependencies and first codec
- [Codec Composition](composition.md) — add compression or encryption on top
