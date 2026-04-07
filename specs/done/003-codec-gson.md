# codec-gson: Gson-backed CodecFactory

## What to build

A Maven module providing a Gson-backed `CodecFactory` implementation. Uses Google's
Gson library for JSON serialization.

### Module: codec-gson

Package: `org.jwcarman.codec.gson`

**GsonCodecFactory** implements `CodecFactory`:
- Constructor takes a `Gson` instance
- `create(Class<T>)`: returns a `GsonCodec<T>` using `gson.toJson()`/`gson.fromJson()`
  with the class
- `create(TypeRef<T>)`: converts `TypeRef.getType()` to Gson's `TypeToken` via
  `TypeToken.get(typeRef.getType())`, then returns a `GsonCodec<T>` using the token

**GsonCodec<T>** implements `Codec<T>`:
- Wraps `Gson` + target type (either `Class<T>` or `TypeToken<T>`)
- `encode`: `gson.toJson(value).getBytes(StandardCharsets.UTF_8)`
- `decode`: `gson.fromJson(new String(bytes, UTF_8), typeToken)` or class
- `type`: returns the raw class
- Thread-safe (Gson instances are thread-safe)

**GsonCodecAutoConfiguration**:
- `@AutoConfiguration(before = CodecAutoConfiguration.class)`
- `@ConditionalOnClass(Gson.class)`
- `@ConditionalOnMissingBean(CodecFactory.class)`
- Creates `GsonCodecFactory` bean. If a `Gson` bean exists, inject it; otherwise
  create a default `new Gson()`

## Acceptance criteria

- [ ] Module compiles and produces a jar
- [ ] `AutoConfiguration.imports` registration exists
- [ ] Auto-config creates `GsonCodecFactory` when Gson is on classpath and no other
  `CodecFactory` exists
- [ ] Auto-config does NOT create factory when another `CodecFactory` is already registered
- [ ] `create(Class<T>)` produces a codec that round-trips simple POJOs
- [ ] `create(TypeRef<T>)` produces a codec that round-trips generic types:
  - `List<String>`
  - `Map<String, Integer>`
  - Nested generic types
- [ ] Codecs handle null fields correctly
- [ ] Codecs handle empty collections correctly
- [ ] `ApplicationContextRunner` test verifies auto-config wiring
- [ ] Spotless passes
- [ ] All tests pass (`./mvnw verify`)
- [ ] Apache 2.0 license headers on all files
- [ ] Module added to `codec-bom` and parent POM

## Implementation notes

- The `TypeRef.getType()` → `TypeToken.get(Type)` bridge is straightforward — Gson's
  `TypeToken` accepts a raw `java.lang.reflect.Type` directly.
- Gson is NOT auto-configured by Spring Boot (unlike Jackson). The auto-config should
  use `@ConditionalOnBean(Gson.class)` as an optional injection, falling back to
  `new Gson()` if no bean exists.
- Dependency: `com.google.code.gson:gson`
