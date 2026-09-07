# Spring Boot

## The starter

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-spring-boot-starter</artifactId>
</dependency>
```

The starter bundles `codec-core`, `codec-transforms`, and `codec-autoconfigure`. Add one backend
module alongside it, and a `CodecFactory` bean is registered for that backend.
The backend modules themselves contain no Spring code — all auto-configuration
lives in `codec-autoconfigure`, including the Redis cache auto-configuration
described in [Spring Data Redis](redis.md#auto-configuration) — that guide's
`codec.redis.cache.*` properties are handled by a `codec-autoconfigure` class
too, not by `codec-spring-data-redis` itself.

The starter also brings `codec-transforms` and `codec-versioned`, the two
dependency-free modules, so compression, text encodings and
[versioned storage](versioned.md) are there without another line in the pom.
What it does not include: `codec-crypto`, `codec-zstd`, and `codec-lz4` each
carry a library of their own, so add whichever of them you need next to the
starter, the same as in a plain-Java build — see
[Codec Composition](composition.md#the-transforms-module) and
[Encryption](encryption.md#quickstart).

## Use it

Inject the auto-configured `CodecFactory` the same way regardless of which
backend activated it:

```java
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;

@Service
public class PersonStore {

    private final Codec<Person> codec;

    public PersonStore(CodecFactory codecFactory) {
        this.codec = codecFactory.create(Person.class);
    }

    public byte[] serialize(Person person) {
        return codec.encode(person);
    }

    public Person deserialize(byte[] bytes) {
        return codec.decode(bytes);
    }
}
```

## Backend selection

Each backend's auto-configuration activates only when both that backend module
and its underlying library are on the classpath. If several backends are
present, precedence is deterministic:

1. Apache Fory (`codec-fory`) — only when the application defines a
   `ThreadSafeFory` bean; that bean is read as intent, so it outranks every
   backend that merely happens to be on the classpath
2. Jackson 3.x (`codec-jackson`)
3. Jackson 2.x (`codec-jackson2`)
4. Gson (`codec-gson`)
5. JSON-B (`codec-jsonb`)
6. Protocol Buffers (`codec-protobuf`)

The normal setup is exactly one backend; the ordering just makes the unusual
case (a backend arriving transitively from another library) predictable. If no
backend is on the classpath, no `CodecFactory` bean exists and injection fails
with `NoSuchBeanDefinitionException` — add exactly one of the modules above.

## Overriding

Every auto-configuration backs off if a `CodecFactory` bean already exists, so
defining your own always wins:

```java
@Bean
CodecFactory codecFactory(ObjectMapper mapper) {
    return new JacksonCodecFactory(mapper);
}
```

## Bean reuse

The Jackson, Gson, and JSON-B configurations reuse the application's existing
`ObjectMapper` / `Gson` / `Jsonb` bean when one exists — so your configured
modules, serialization features, and naming strategies apply to codecs too. When
no such bean exists, a default instance is created instead.

## Where next

- [Getting Started](getting-started.md) — dependencies and first codec
- [Apache Fory](fory.md#spring-boot) — the one backend that needs a bean to
  activate at all
- [Spring Data Redis](redis.md#auto-configuration) — the cache
  auto-configuration this starter also brings in
- [Codec Composition](composition.md) — compression, encryption, and
  versioning on top of the auto-configured `CodecFactory`
