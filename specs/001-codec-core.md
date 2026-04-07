# codec-core: SPI, TypeRef, built-in codecs, auto-configuration, and BOM

## What to build

Create the foundational `codec-core` module, `codec-bom`, and parent POM for the
Codec project. This is the base everything else depends on.

### Parent POM

Multi-module Maven project mirroring Odyssey/Substrate's structure:

- `groupId`: `org.jwcarman.codec`
- `artifactId`: `codec-parent`
- `version`: `1.0.0-SNAPSHOT`
- Java 25, Spring Boot 4.x parent
- Plugins: Spotless (Google Java Format, `check` goal at `validate` phase), Surefire
  (with mockito/byte-buddy javaagents), Failsafe, maven-compiler-plugin (Spring Boot
  annotation processors), license-maven-plugin (Apache 2.0 headers)
- Profiles: `release` (Maven Central publishing via sonatype-central-publishing-maven-plugin,
  artifact signing), `ci` (JaCoCo code coverage + Sonar scanning), `license` (header formatting)
- Module list includes `codec-bom` and `codec-core` (other modules added by later specs)

Use Odyssey's parent POM (`/Users/jcarman/IdeaProjects/odyssey/pom.xml`) as the direct
template for plugin configuration, profiles, and dependency management.

### codec-bom

A `<dependencyManagement>`-only POM listing all Codec modules for version alignment.
Start with just `codec-core`; later specs will add their modules.

### SPI interfaces

Package: `org.jwcarman.codec.spi`

**Codec<T>** — type-safe encoder/decoder:

```java
public interface Codec<T> {
    byte[] encode(T value);
    T decode(byte[] bytes);
    Class<T> type();
}
```

**CodecFactory** — produces codecs for any type:

```java
public interface CodecFactory {
    <T> Codec<T> create(Class<T> type);
    <T> Codec<T> create(TypeRef<T> typeRef);
}
```

**TypeRef<T>** — generic type capture via anonymous subclass pattern:

Package: `org.jwcarman.codec.spi`

```java
public abstract class TypeRef<T> {
    private final Type type;

    protected TypeRef() {
        Type superclass = getClass().getGenericSuperclass();
        this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    }

    public Type getType() { return type; }
}
```

Usage: `new TypeRef<List<String>>() {}`

### Built-in codecs

Package: `org.jwcarman.codec.builtin`

**StringCodec** — passthrough for String values:
- `encode`: `value.getBytes(StandardCharsets.UTF_8)`
- `decode`: `new String(bytes, StandardCharsets.UTF_8)`
- `type`: `String.class`

**ByteArrayCodec** — identity codec for byte arrays:
- `encode`: returns the input array (or defensive copy)
- `decode`: returns the input array (or defensive copy)
- `type`: `byte[].class`

### Auto-configuration

Package: `org.jwcarman.codec.autoconfigure`

**CodecAutoConfiguration**:
- `@AutoConfiguration`
- Registers `StringCodec` and `ByteArrayCodec` as beans
- Does NOT provide a fallback `CodecFactory` — there's no sensible generic default.
  If no backend module is on the classpath, there's no `CodecFactory` bean, and
  injection will fail with a clear Spring error.
- Register in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## Acceptance criteria

- [ ] Parent POM builds successfully (`./mvnw validate`)
- [ ] `codec-bom` POM exists with `codec-core` in dependency management
- [ ] `Codec<T>` interface compiles with correct generic signatures
- [ ] `CodecFactory` interface compiles with both `Class<T>` and `TypeRef<T>` methods
- [ ] `TypeRef<T>` correctly captures generic types via anonymous subclass (test with
  `List<String>`, `Map<String, Integer>`, nested generics)
- [ ] `StringCodec` round-trips String values through encode/decode
- [ ] `ByteArrayCodec` round-trips byte arrays through encode/decode
- [ ] `CodecAutoConfiguration` registers `StringCodec` and `ByteArrayCodec` beans
  (test with `ApplicationContextRunner`)
- [ ] No `CodecFactory` bean is registered by core auto-config
- [ ] Spotless passes (`./mvnw spotless:check`)
- [ ] All tests pass (`./mvnw verify`)
- [ ] Apache 2.0 license headers on all Java and POM files

## Implementation notes

- Use Odyssey's parent POM as the template: `/Users/jcarman/IdeaProjects/odyssey/pom.xml`
- `TypeRef` follows the same pattern as Jackson's `TypeReference` and Gson's `TypeToken`.
  The key is that the anonymous subclass captures the generic type argument at the
  class level, which survives type erasure.
- Built-in codecs are simple and don't need a `CodecFactory` — they're registered
  directly as beans for convenience.
