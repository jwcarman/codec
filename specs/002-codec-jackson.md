# codec-jackson: Jackson-backed CodecFactory

## What to build

A Maven module providing a Jackson-backed `CodecFactory` implementation. Uses
Jackson's `ObjectMapper` for JSON serialization. This is the most common backend
and the one most Spring Boot users will reach for.

### Module: codec-jackson

Package: `org.jwcarman.codec.jackson`

**JacksonCodecFactory** implements `CodecFactory`:
- Constructor takes an `ObjectMapper`
- `create(Class<T>)`: returns a `JacksonCodec<T>` that uses
  `objectMapper.writeValueAsBytes(value)` / `objectMapper.readValue(bytes, type)`
- `create(TypeRef<T>)`: converts `TypeRef.getType()` to Jackson's `JavaType` via
  `objectMapper.getTypeFactory().constructType(typeRef.getType())`, then returns a
  `JacksonCodec<T>` using the constructed `JavaType`

**JacksonCodec<T>** implements `Codec<T>`:
- Wraps `ObjectMapper` + target type (either `Class<T>` or `JavaType`)
- `encode`: `objectMapper.writeValueAsBytes(value)`
- `decode`: `objectMapper.readValue(bytes, javaType)`
- `type`: returns the raw class
- Thread-safe (ObjectMapper is thread-safe for read operations)

**JacksonCodecAutoConfiguration**:
- `@AutoConfiguration(before = CodecAutoConfiguration.class)`
- `@ConditionalOnClass(ObjectMapper.class)`
- `@ConditionalOnMissingBean(CodecFactory.class)`
- Creates `JacksonCodecFactory` bean, injecting the existing `ObjectMapper` bean
  (Spring Boot auto-configures one by default)

**JacksonCodecProperties** — `@ConfigurationProperties(prefix = "codec.jackson")`:
- Minimal or empty for now — Jackson config is typically done via `ObjectMapper`
  customization, not codec-level properties

## Acceptance criteria

- [ ] Module compiles and produces a jar
- [ ] `AutoConfiguration.imports` registration exists
- [ ] Auto-config creates `JacksonCodecFactory` when Jackson is on classpath
- [ ] Auto-config does NOT create factory when another `CodecFactory` is already registered
- [ ] `create(Class<T>)` produces a codec that round-trips simple POJOs (record with
  String/int/boolean fields)
- [ ] `create(TypeRef<T>)` produces a codec that round-trips generic types:
  - `List<String>`
  - `Map<String, Integer>`
  - `List<MyRecord>`
- [ ] Codecs handle null fields correctly
- [ ] Codecs handle empty collections correctly
- [ ] Codec `type()` returns the correct raw class
- [ ] Thread safety: multiple threads encoding/decoding concurrently produce correct results
- [ ] `ApplicationContextRunner` test verifies auto-config wiring with Spring Boot's
  default `ObjectMapper`
- [ ] Spotless passes
- [ ] All tests pass (`./mvnw verify`)
- [ ] Apache 2.0 license headers on all files
- [ ] Module added to `codec-bom` and parent POM

## Implementation notes

- Jackson's `ObjectMapper` is already auto-configured by Spring Boot. The
  `JacksonCodecAutoConfiguration` just wraps it — no need to create a new one.
- The `TypeRef` → `JavaType` bridge is the key piece. Jackson's `TypeFactory.constructType(Type)`
  handles `ParameterizedType` from `TypeRef.getType()` directly.
- Dependencies: `tools.jackson.core:jackson-databind` (provided by Spring Boot)
