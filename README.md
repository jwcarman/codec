# Codec

[![CI](https://github.com/jwcarman/codec/actions/workflows/maven.yml/badge.svg)](https://github.com/jwcarman/codec/actions/workflows/maven.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/dynamic/xml?url=https://raw.githubusercontent.com/jwcarman/codec/main/pom.xml&query=//*[local-name()='java.version']/text()&label=Java&color=orange)](https://openjdk.org/)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_codec&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_codec)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_codec&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_codec)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_codec&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_codec)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_codec&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=jwcarman_codec)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_codec&metric=coverage)](https://sonarcloud.io/summary/new_code?id=jwcarman_codec)

Type-safe serialization abstraction for Java. Codec provides a simple, generic `Codec<T>`
interface that your libraries depend on, letting applications choose their serialization
framework (Jackson, Gson, Protocol Buffers, etc.) without coupling library code to a
specific implementation.

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
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add the backend you want:

```xml
<!-- Jackson (JSON) -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-jackson</artifactId>
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

## Modules

| Module | Backend | Artifact |
|--------|---------|----------|
| Core | SPI interfaces (`Codec`, `CodecFactory`, `TypeRef`) | `codec-core` |
| Jackson | Jackson JSON | `codec-jackson` |
| Gson | Gson JSON | `codec-gson` |
| Protobuf | Protocol Buffers | `codec-protobuf` |

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

Each backend module provides Spring Boot auto-configuration. Drop the dependency
on the classpath and a `CodecFactory` bean is registered automatically.

Jackson uses the application's existing `ObjectMapper`. Gson creates a default
`Gson` instance if none is available.

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
