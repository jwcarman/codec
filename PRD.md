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
- Spring Boot auto-configuration — via `codec-spring-boot-starter`; backend
  modules stay Spring-free, and adding a backend to the classpath registers it
- Codec composition — `codec.andThen(byteTransform)` layers a `Codec<byte[]>`
  (compression, encryption, etc.) onto any `Codec<T>`; gzip ships built in
- Published to Maven Central with BOM for version alignment

## Non-Goals

- Schema registry integration
- Schema evolution/migration tools
- Streaming serialization (large payloads)
- Compression beyond built-in gzip (other algorithms are consumer-supplied
  `Codec<byte[]>` transforms via `andThen`; encryption is no longer
  consumer-supplied — `codec-crypto` ships an AES-256-GCM envelope-encryption
  transform)

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
    default Codec<T> andThen(Codec<byte[]> transform) { ... }
}
```

- `encode`: serializes a value to bytes
- `decode`: deserializes bytes back to the original type
- `andThen`: layers a byte-level transform (compression, encryption, etc.) onto
  this codec, applied after `encode` and unwound before `decode`; chained
  transforms unwind automatically in reverse order

### CodecFactory — produces codecs for any type

```java
public interface CodecFactory {
    <T> Codec<T> create(TypeRef<T> typeRef);

    default <T> Codec<T> create(Class<T> type) {
        return create(TypeRef.of(type));
    }
}
```

- `create(TypeRef<T>)`: the abstract method; for generic types (e.g.,
  `new TypeRef<List<String>>() {}`)
- `create(Class<T>)`: a default method for simple types without generics,
  delegating to `create(TypeRef<T>)`

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
├── codec-core/                   # SPI, TypeRef, compression transforms
│   ├── spi/
│   │   ├── Codec.java
│   │   ├── CodecFactory.java
│   │   └── TypeRef.java
│   └── autoconfigure/
│       └── CodecAutoConfiguration.java
│
├── codec-crypto/                 # Envelope-encryption Codec<byte[]> transform
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
| Jackson | `codec-jackson` |
| Gson | `codec-gson` |
| Protobuf | `codec-protobuf` |
| Envelope encryption transform | `codec-crypto` |

### Auto-configuration

Each backend module self-registers via `@AutoConfiguration` with
`@ConditionalOnClass`. `codec-core` does NOT provide a fallback CodecFactory —
there's no sensible generic default.

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
version:     0.2.0-SNAPSHOT
Java:        25
Spring Boot: 4.x
```

---

## Future Considerations

- **Substrate integration** — Substrate's SPIs will eventually accept `Codec<T>` to
  serialize/deserialize journal entries and mailbox values.
- **Avro** — could be a backend, but schema registry dependency makes it heavier.
- **MessagePack** — not a future module: it's a `JsonFactory` swap on the
  existing Jackson backend, not a new `CodecFactory` implementation.
- **Kryo** — fast Java serialization, but not cross-language. Worth considering for
  JVM-only deployments.
