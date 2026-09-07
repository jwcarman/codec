# Codec

[![CI](https://github.com/jwcarman/codec/actions/workflows/maven.yml/badge.svg)](https://github.com/jwcarman/codec/actions/workflows/maven.yml)
[![CodeQL](https://github.com/jwcarman/codec/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/jwcarman/codec/actions/workflows/github-code-scanning/codeql)
[![Maven Central](https://img.shields.io/maven-central/v/org.jwcarman.codec/codec-core)](https://central.sonatype.com/artifact/org.jwcarman.codec/codec-core)
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

**📖 Documentation: [jwcarman.github.io/codec](https://jwcarman.github.io/codec/) · API reference: [jwcarman.github.io/codec/api](https://jwcarman.github.io/codec/api/)**

## Requirements

- Java 25+
- Spring Boot 4.x (only if you use the starter — the backends themselves are
  Spring-free)

## Quick Start

### 1. Add the dependency

> [!NOTE]
> The dependency snippets below name the latest release. This repository's
> `main` branch tracks ahead of it — anything the
> [Changelog](https://github.com/jwcarman/codec/blob/main/CHANGELOG.md) lists
> under *Unreleased* is not in that release yet.

Use the BOM for version alignment:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.jwcarman.codec</groupId>
            <artifactId>codec-bom</artifactId>
            <version>0.7.0</version>
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

<!-- Jackson 2 (JSON, for projects on Jackson 2, com.fasterxml.jackson) -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-jackson2</artifactId>
</dependency>

<!-- Gson (JSON) -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-gson</artifactId>
</dependency>

<!-- JSON-B (Jakarta JSON Binding; bring a provider, e.g. Yasson) -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-jsonb</artifactId>
</dependency>

<!-- Protocol Buffers -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-protobuf</artifactId>
</dependency>

<!-- Apache Fory (JVM-only binary; classes must be registered, so build the
     factory with ForyCodecFactory.of(Person.class, ...) or your own Fory) -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-fory</artifactId>
</dependency>
```

### 2. Use it

Inject a `CodecFactory` and create codecs for your types:

```java
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;

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

Layer any `Codec<byte[]>` transform (compression, encryption, a custom one)
onto a codec with `andThen`. Encoding applies transforms left to right;
decoding inverts them automatically. See
[Codec Composition](https://jwcarman.github.io/codec/guides/composition/) for
the built-in compression transforms, decompression-bomb protection, and how to
write your own:

```java
Codec<Person> codec = codecFactory.create(Person.class).andThen(new GzipCodec());
```

To change your storage strategy later without rewriting stored data, wrap the
codec in a versioned one (`codec-versioned`). It writes a 3-byte header naming
the version that produced each payload and dispatches on it when reading, so old
data stays readable while new data is written by the new strategy:

```java
Codec<Person> codec = VersionedCodec.<Person>builder()
        .version(1, jacksonFactory.create(Person.class))
        .version(2, foryFactory.create(Person.class).andThen(new ZstdCodec()))
        .writing(2)   // deploy readers first, then flip this
        .build();
```

## Modules

| Module | What it is | Artifact |
|--------|------------|----------|
| Core | SPI interfaces (`Codec`, `CodecFactory`, `TypeRef`) | `codec-core` |
| Transforms | Byte transforms with no dependencies beyond `codec-core`: gzip, deflate, Base64, Base32, hex, checksum, text | `codec-transforms` |
| Versioned | Format versioning: a version header that lets the storage strategy change | `codec-versioned` |
| Jackson | Jackson 3.x JSON (`tools.jackson`) | `codec-jackson` |
| Jackson 2 | Jackson 2.x JSON (`com.fasterxml.jackson`) | `codec-jackson2` |
| Gson | Gson JSON | `codec-gson` |
| JSON-B | Jakarta JSON Binding | `codec-jsonb` |
| Protobuf | Protocol Buffers | `codec-protobuf` |
| Fory | Apache Fory binary (JVM-only, registration required) | `codec-fory` |
| Crypto | AES-256-GCM envelope encryption transform | `codec-crypto` |
| Zstandard | Zstandard compression transform | `codec-zstd` |
| LZ4 | LZ4 frame compression transform | `codec-lz4` |
| Spring Data Redis | `RedisSerializer` adapter, cache auto-configuration | `codec-spring-data-redis` |
| Kafka | `Serializer`, `Deserializer`, and `Serde` adapters | `codec-kafka` |
| Auto-configure | Spring Boot auto-configuration for all backends | `codec-autoconfigure` |
| Starter | Spring Boot starter (core + auto-configure) | `codec-spring-boot-starter` |

Speed and ratio claims are backed by [benchmarks](https://jwcarman.github.io/codec/benchmarks/).

## Core SPI

The core module provides three types:

### `Codec<T>`

```java
public interface Codec<T> {
    byte[] encode(T value);
    T decode(byte[] bytes);
}
```

Every `encode`/`decode` failure is a `CodecException` in one of four families —
fix the value, quarantine the payload, hold it for a newer reader, or retry —
keyed to what the caller does next. See
[Handling Failures](https://jwcarman.github.io/codec/guides/error-handling/).

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
modules themselves are Spring-free. See
[Spring Boot](https://jwcarman.github.io/codec/guides/spring-boot/) for
backend precedence, bean reuse, and what the starter does not include.

## Building

```bash
# Compile and run tests
./mvnw clean verify

# Apply code formatting
./mvnw spotless:apply

# Apply license headers
./mvnw -Plicense license:format
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
