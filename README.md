# Codec

[![CI](https://github.com/jwcarman/codec/actions/workflows/maven.yml/badge.svg)](https://github.com/jwcarman/codec/actions/workflows/maven.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/dynamic/xml?url=https://raw.githubusercontent.com/jwcarman/codec/main/pom.xml&query=//*[local-name()='maven.compiler.release']/text()&label=Java&color=orange)](https://openjdk.org/)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_codec&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_codec)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_codec&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_codec)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_codec&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_codec)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_codec&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=jwcarman_codec)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_codec&metric=coverage)](https://sonarcloud.io/summary/new_code?id=jwcarman_codec)

Type-safe serialization abstraction for Java. Codec provides a simple, generic `Codec<T>`
interface that your libraries depend on, letting applications choose their serialization
framework (Jackson, Gson, Protocol Buffers, etc.) without coupling library code to a
specific implementation.

**📖 Documentation: [jwcarman.github.io/codec](https://jwcarman.github.io/codec/)**

## Requirements

- Java 25+
- Spring Boot 4.x (for auto-configuration)

## Quick Start

### 1. Add the dependency

Use the BOM for version alignment:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.jwcarman.codec</groupId>
            <artifactId>codec-bom</artifactId>
            <version>0.5.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

For Spring Boot applications, add the starter (it brings the auto-configuration),
then the backend you want:

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-spring-boot-starter</artifactId>
</dependency>
```

Outside Spring, skip the starter and just add a backend — construct its factory
directly (e.g. `new JacksonCodecFactory(objectMapper)`). Backend choices:

```xml
<!-- Jackson 3 (JSON) -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-jackson</artifactId>
</dependency>

<!-- Jackson 2 (JSON, for projects still on com.fasterxml Jackson) -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-jackson2</artifactId>
</dependency>

<!-- Gson (JSON) -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-gson</artifactId>
</dependency>

<!-- Protocol Buffers -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-protobuf</artifactId>
</dependency>
```

### 2. Use it

Inject a `CodecFactory` and create codecs for your types:

```java
@Service
public class MyService {

    private final Codec<Person> personCodec;

    public MyService(CodecFactory codecFactory) {
        this.personCodec = codecFactory.create(Person.class);
    }

    public byte[] serialize(Person person) {
        return personCodec.encode(person);
    }

    public Person deserialize(byte[] bytes) {
        return personCodec.decode(bytes);
    }
}
```

For generic types, use `TypeRef`:

```java
Codec<List<Person>> codec = codecFactory.create(new TypeRef<List<Person>>() {});
```

### 3. Compose codecs

Layer any `Codec<byte[]>` transform (compression, encryption, …) onto a codec
with `andThen`. Encoding applies transforms left to right; decoding inverts
them automatically. Gzip ships built in:

```java
Codec<Person> codec = codecFactory.create(Person.class).andThen(new GzipCodec());
```

Two compression transforms ship built in: `GzipCodec` (gzip framing, best for
interop) and `DeflateCodec` (zlib framing, ~12 bytes less overhead per payload,
with an optional compression level: `new DeflateCodec(Deflater.BEST_COMPRESSION,
maxDecodedSize)`). Both extend `CompressionStreamCodec`, which you can subclass
to wrap any stream-based compression library in two one-line methods.

To guard against decompression bombs, both codecs refuse to decode payloads
that expand beyond 64 MiB by default; pass a byte limit to the constructor
(e.g. `new GzipCodec(maxDecodedSize)`) to raise or lower the cap. The cap is
enforced by `CompressionStreamCodec`, so subclasses inherit it automatically.

Bring your own transform by implementing `Codec<byte[]>` — `encode` is the
forward direction (e.g. encrypt), `decode` its inverse:

```java
Codec<Person> codec =
    codecFactory.create(Person.class)
        .andThen(new GzipCodec())
        .andThen(new AesCodec(key)); // your own Codec<byte[]>
```

## Modules

| Module | Backend | Artifact |
|--------|---------|----------|
| Core | SPI interfaces (`Codec`, `CodecFactory`, `TypeRef`) | `codec-core` |
| Jackson | Jackson 3.x JSON (`tools.jackson`) | `codec-jackson` |
| Jackson 2 | Jackson 2.x JSON (`com.fasterxml.jackson`) | `codec-jackson2` |
| Gson | Gson JSON | `codec-gson` |
| JSON-B | Jakarta JSON Binding | `codec-jsonb` |
| Protobuf | Protocol Buffers | `codec-protobuf` |
| Crypto | AES-256-GCM envelope encryption transform | `codec-crypto` |
| Zstandard | Zstandard compression transform | `codec-zstd` |
| Auto-configure | Spring Boot auto-configuration for all backends | `codec-autoconfigure` |
| Starter | Spring Boot starter (core + auto-configure) | `codec-spring-boot-starter` |

## Core SPI

The core module provides three types:

### `Codec<T>`

```java
public interface Codec<T> {
    byte[] encode(T value);
    T decode(byte[] bytes);
}
```

### `CodecFactory`

```java
public interface CodecFactory {
    <T> Codec<T> create(TypeRef<T> typeRef);

    default <T> Codec<T> create(Class<T> type) {
        return create(TypeRef.of(type));
    }
}
```

### `TypeRef<T>`

A super type token that captures generic type information at runtime:

```java
// Simple types
TypeRef<String> ref = TypeRef.of(String.class);

// Generic types
TypeRef<List<String>> ref = new TypeRef<>() {};

// Nested generics
TypeRef<Map<String, List<Integer>>> ref = new TypeRef<>() {};
```

`TypeRef` implements `equals()` and `hashCode()` based on the captured `Type`,
making it safe to use as a map key for caching codecs.

## Auto-Configuration

The `codec-spring-boot-starter` (via `codec-autoconfigure`) registers a
`CodecFactory` bean for whichever backend is on the classpath. The backend
modules themselves are Spring-free.

When several backends are present, precedence is Jackson 3 → Jackson 2 → Gson →
JSON-B → Protobuf, and defining your own `CodecFactory` bean always wins. The
Jackson, Gson, and JSON-B configurations reuse the application's
`ObjectMapper`/`Gson`/`Jsonb` bean when one exists, falling back to a default
instance otherwise.

## Building

```bash
# Compile and run tests
mvn clean verify

# Apply code formatting
mvn spotless:apply

# Apply license headers
mvn -Plicense license:format
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
