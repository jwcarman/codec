# Codec

**Type-safe serialization abstraction for Java.**

Codec decouples your application code from specific serialization libraries. You
work with two small interfaces — `Codec<T>` and `CodecFactory` — and choose the
backend (Jackson, Gson, Protocol Buffers) by picking a module.

```java
Codec<Person> codec = codecFactory.create(Person.class);

byte[] bytes = codec.encode(person);
Person decoded = codec.decode(bytes);
```

## Why

Building-block libraries often need to serialize values — cache entries, queue
payloads, journal records — without dictating *how*. Hard-coding Jackson (or
anything else) forces that choice onto every consumer. Codec keeps the contract
tiny and pushes the backend decision to the classpath.

## Features

- **Clean SPI** — `Codec<T>` (encode/decode) and `CodecFactory` (produce codecs
  for any type)
- **Full generic support** — `TypeRef<T>` captures parameterized types, so
  `List<Person>` round-trips without erasure surprises
- **Pluggable backends** — Jackson 3.x, Jackson 2.x, Gson, and Protocol Buffers
  modules, each Spring-free
- **Codec composition** — layer compression or encryption onto any codec with
  `andThen`; gzip and deflate transforms ship built in, with decompression-bomb
  protection
- **Spring Boot auto-configuration** — add the starter and a backend, get a
  `CodecFactory` bean
- **BOM for version alignment** and stable `Automatic-Module-Name`s for JPMS
  consumers

## Modules

| Module | What it is |
|--------|------------|
| `codec-core` | The SPI: `Codec`, `CodecFactory`, `TypeRef`, and the compression transforms |
| `codec-jackson` | Jackson 3.x (`tools.jackson`) JSON backend |
| `codec-jackson2` | Jackson 2.x (`com.fasterxml.jackson`) JSON backend |
| `codec-gson` | Gson JSON backend |
| `codec-protobuf` | Protocol Buffers backend |
| `codec-autoconfigure` | Spring Boot auto-configuration for all backends |
| `codec-spring-boot-starter` | Starter bundling core + auto-configuration |
| `codec-bom` | Bill of materials for version alignment |

## Where next

- [Getting Started](guides/getting-started.md) — dependencies and first codec
- [Codec Composition](guides/composition.md) — compression, encryption, and
  custom transforms
- [Spring Boot](guides/spring-boot.md) — auto-configuration details
