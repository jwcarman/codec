# Jackson Dataformats

`JacksonCodecFactory` and `Jackson2CodecFactory` take any `ObjectMapper`. Every
Jackson dataformat ships a mapper subclass, so **CBOR, Smile, YAML, and XML are
backends by mapper swap** — no new module, nothing in Codec changes:

```java
CodecFactory factory = new JacksonCodecFactory(CBORMapper.builder().build());

Codec<Person> codec = factory.create(Person.class);   // CBOR bytes in, CBOR bytes out
```

The same works for Jackson 2.x with `Jackson2CodecFactory` and the
`com.fasterxml.jackson.dataformat` mappers.

## Dependencies

Add the dataformat next to the backend. Versions come from the Jackson BOM, so
none are needed when you import `codec-bom` alongside a Spring Boot or Jackson
BOM.

| Format | Jackson 3.x artifact (`tools.jackson.dataformat`) | Mapper | Good for |
|--------|---------------------------------------------------|--------|----------|
| CBOR | `jackson-dataformat-cbor` | `CBORMapper` | Compact binary JSON (RFC 8949); interoperable with non-Java systems |
| Smile | `jackson-dataformat-smile` | `SmileMapper` | Jackson's own binary JSON; fastest of the Jackson formats, Java-to-Java |
| YAML | `jackson-dataformat-yaml` | `YAMLMapper` | Human-editable payloads and configuration |
| XML | `jackson-dataformat-xml` | `XmlMapper` | Systems that speak XML but not JAXB |

For Jackson 2.x the group is `com.fasterxml.jackson.dataformat` and the mapper
classes live under `com.fasterxml.jackson.dataformat.<format>`.

## What round-trips

Records, beans, and `TypeRef` generics all round-trip through every format
above — the module tests prove it for `Person`, `Map<String, Person>`, and
(for the binary formats) `List<Person>`. See `JacksonDataformatsTest` in
`codec-jackson` and `Jackson2DataformatsTest` in `codec-jackson2`.

!!! note "XML and root-level collections"
    XML has no natural representation for a root-level list. `List<Person>`
    round-trips through CBOR, Smile, and YAML; for XML, wrap collections in a
    record or use a `Map`.

## Choosing

- Talking to other languages: **CBOR**. It is an IETF standard with libraries
  everywhere.
- Java on both ends and you want the smallest, fastest Jackson output:
  **Smile**.
- A human will read or edit it: **YAML**.
- Otherwise, plain JSON — it is what every tool understands, and gzip or
  [zstd](composition.md#built-in-compression) closes most of the size gap.

All of these compose with the transforms in the same way JSON does:

```java
Codec<Order> codec = new JacksonCodecFactory(SmileMapper.builder().build())
    .create(Order.class)
    .andThen(new ZstdCodec());
```

## Where next

- [Codec Composition](composition.md) — compression, encryption, and text-safe
  output
- [Getting Started](getting-started.md) — dependencies and first codec
