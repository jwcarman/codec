# PRD — Codec

---

## What this project is

Codec is a type-safe serialization abstraction for Java. It provides a clean SPI —
`Codec<T>` and `CodecFactory` — that decouples application code from specific
serialization libraries (Jackson, Gson, Protobuf, etc.).

Codec was born from the need to give building-block libraries like Substrate a
serialization layer without coupling them to any specific implementation. Consumers
choose their serialization backend by dropping a module on the classpath.

---

## Goals

- Clean SPI: `Codec<T>` (encode/decode) and `CodecFactory` (produce codecs for any type)
- `TypeRef<T>` for full generic type support (avoids type erasure)
- Mix-and-match backends — Jackson, Gson, Protobuf, etc.
- Spring Boot auto-configuration — drop a backend on the classpath, it registers
- In-memory/passthrough fallback for `String` and `byte[]`
- Published to Maven Central with BOM for version alignment

## Non-Goals

- Schema registry integration
- Schema evolution/migration tools
- Streaming serialization (large payloads)
- Compression (consumers handle that)

---

## Tech stack

- Language: Java 25
- Framework: Spring Boot 4.x
- Build tool: Maven (multi-module)
- Testing: JUnit 5 + Mockito + AssertJ
- Linting / formatting: Spotless with Google Java Format
- License: Apache 2.0

---

## SPI

### Codec<T> — type-safe encoder/decoder

```java
public interface Codec<T> {
    byte[] encode(T value);
    T decode(byte[] bytes);
    Class<T> type();
}
```

- `encode`: serializes a value to bytes
- `decode`: deserializes bytes back to the original type
- `type`: returns the raw class this codec handles

### CodecFactory — produces codecs for any type

```java
public interface CodecFactory {
    <T> Codec<T> create(Class<T> type);
    <T> Codec<T> create(TypeRef<T> typeRef);
}
```

- `create(Class<T>)`: for simple types without generics
- `create(TypeRef<T>)`: for generic types (e.g., `new TypeRef<List<String>>() {}`)

### TypeRef<T> — generic type capture

```java
public abstract class TypeRef<T> {
    private final Type type;

    protected TypeRef() {
        // extract T from subclass via reflection
        this.type = extractType();
    }

    public Type getType() { return type; }
}
```

Anonymous subclass pattern (like Jackson's `TypeReference`, Gson's `TypeToken`):
```java
Codec<List<String>> codec = factory.create(new TypeRef<List<String>>() {});
```

---

## Module Structure

```
codec/
├── codec-bom/                    # BOM for version alignment
├── codec-core/                   # SPI, TypeRef, StringCodec, auto-config
│   ├── spi/
│   │   ├── Codec.java
│   │   ├── CodecFactory.java
│   │   └── TypeRef.java
│   ├── builtin/
│   │   ├── StringCodec.java
│   │   └── ByteArrayCodec.java
│   └── autoconfigure/
│       └── CodecAutoConfiguration.java
│
├── codec-jackson/                # Jackson ObjectMapper backend
├── codec-gson/                   # Gson backend
├── codec-protobuf/               # Protobuf backend
│
└── codec-example/                # Example app (not published)
```

Each module is independently deployable. Only one CodecFactory implementation
should be on the classpath at a time (auto-config uses @ConditionalOnMissingBean).

| Backend | Module |
|---------|--------|
| Built-in (String/byte[]) | `codec-core` |
| Jackson | `codec-jackson` |
| Gson | `codec-gson` |
| Protobuf | `codec-protobuf` |

### Auto-configuration

Each backend module self-registers via `@AutoConfiguration` with
`@ConditionalOnClass`. `codec-core` does NOT provide a fallback CodecFactory
(there's no sensible generic default) — it only provides `StringCodec` and
`ByteArrayCodec` as convenience beans.

---

## Configuration

```yaml
codec:
  jackson:
    # Jackson-specific config (if any)
  gson:
    # Gson-specific config (if any)
```

Minimal configuration expected — most users just drop the module on the classpath
and it works.

---

## Coding conventions

- Immutable domain objects (records where possible)
- No reactive types — virtual threads throughout
- No `@SuppressWarnings` annotations — fix the underlying issue
- `@ConfigurationProperties` as records with defaults in properties files
- Apache 2.0 license headers on all files
- Google Java Format via Spotless

---

## Definition of "done" for a spec

A spec is done when ALL of the following are true:

- [ ] The feature described in the spec is implemented
- [ ] All existing tests pass (`./mvnw verify`)
- [ ] New tests exist for the new behavior
- [ ] Spotless passes (`./mvnw spotless:check`)
- [ ] No debug code left in
- [ ] progress.txt is updated with verification results

---

## Constraints and guardrails

- Backend auto-configurations use `@ConditionalOnClass` only — no `@ConditionalOnProperty`
- All new Java files and POM files must include Apache 2.0 license headers
- `@ConfigurationProperties` must be records with defaults in `*-defaults.properties`
- Codec implementations must be thread-safe
- `TypeRef` must correctly capture generic types through anonymous subclasses

---

## Maven Coordinates

```
groupId:     org.jwcarman.codec
artifactId:  codec-parent
version:     1.0.0-SNAPSHOT
Java:        25
Spring Boot: 4.x
```

---

## Future Considerations

- **Substrate integration** — Substrate's SPIs will eventually accept `Codec<T>` to
  serialize/deserialize journal entries and mailbox values.
- **Avro** — could be a backend, but schema registry dependency makes it heavier.
- **MessagePack** — lightweight binary format, good candidate for a future module.
- **Kryo** — fast Java serialization, but not cross-language. Worth considering for
  JVM-only deployments.
