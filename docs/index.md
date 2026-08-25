---
hide:
  - navigation
  - toc
---

<div class="jw-hero" markdown>

# Codec

<p class="jw-hero__tagline">Type-safe serialization abstraction for Java.</p>

<div class="jw-tape" data-word="codec" role="img" aria-label="The word codec, shown as its bytes: 63 6F 64 65 63"></div>

<p class="jw-hero__actions">
  <a class="md-button md-button--primary" href="guides/getting-started/">Get started</a>
  <a class="md-button" href="https://github.com/jwcarman/codec">View on GitHub</a>
</p>

</div>

Two small interfaces — `Codec<T>` and `CodecFactory` — sit between your code and
whichever serialization library you pick. Choose the backend by adding a module;
nothing in your code changes.

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

## What you get

<div class="grid cards" markdown>

-   **A clean SPI**

    ---

    `Codec<T>` encodes and decodes. `CodecFactory` produces a codec for any
    type. That is the whole contract.

-   **Generics that round-trip**

    ---

    `TypeRef<T>` captures parameterized types, so `List<Person>` comes back as
    `List<Person>` — no erasure surprises.

-   **Pluggable backends**

    ---

    Jackson 3.x, Jackson 2.x, Gson, and Protocol Buffers, each in its own
    Spring-free module.

-   **Composition**

    ---

    Layer compression or encryption onto any codec with `andThen`. Gzip and
    deflate ship built in, with decompression-bomb protection.

-   **Envelope encryption**

    ---

    AES-256-GCM with pluggable key management — in-process keys or your KMS —
    and a published [threat model](guides/threat-model.md).

-   **Spring Boot auto-configuration**

    ---

    Add the starter and a backend; get a `CodecFactory` bean. A BOM keeps
    versions aligned.

</div>

## Modules

| Module | What it is |
|--------|------------|
| `codec-core` | The SPI: `Codec`, `CodecFactory`, `TypeRef`, and the compression transforms |
| `codec-jackson` | Jackson 3.x (`tools.jackson`) JSON backend |
| `codec-jackson2` | Jackson 2.x (`com.fasterxml.jackson`) JSON backend |
| `codec-gson` | Gson JSON backend |
| `codec-protobuf` | Protocol Buffers backend |
| `codec-crypto` | AES-256-GCM envelope encryption with pluggable key management |
| `codec-autoconfigure` | Spring Boot auto-configuration for all backends |
| `codec-spring-boot-starter` | Starter bundling core + auto-configuration |
| `codec-bom` | Bill of materials for version alignment |

## Where next

- [Getting Started](guides/getting-started.md) — dependencies and first codec
- [Codec Composition](guides/composition.md) — compression, encryption, and
  custom transforms
- [Encryption](guides/encryption.md) — envelope encryption and key management
- [Spring Boot](guides/spring-boot.md) — auto-configuration details
