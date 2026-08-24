# GraalVM native-image hints for codec

## Context

Exercised against `cowork-connector-example` (Spring Boot 4.0.5, Java 25) using the GraalVM tracing agent (`-agentlib:native-image-agent`). Codec 0.1.0 currently ships **zero** `META-INF/native-image/` metadata and **no** `RuntimeHintsRegistrar` / `BeanRegistrationAotProcessor`.

## Agent-captured surface (4 entries)

- `codec-core` — `CodecFactory` (iface)
- `codec-jackson` — `JacksonCodec`, `JacksonCodecFactory`, `JacksonCodecAutoConfiguration`, `JacksonCodecProperties`

## Coverage analysis

| Category | Handled by |
|---|---|
| Auto-config + `@ConfigurationProperties` | ✅ Spring AOT |
| SPI interfaces + concrete Spring beans (`Codec<T>`, `CodecFactory`, `JacksonCodec`, `JacksonCodecFactory`) | ✅ Spring AOT |
| Binding hints on `T` for every `codecFactory.create(TypeRef<T>)` call | ✅ Consumer responsibility |

## What codec needs to ship

**Nothing — at least for this exercise.**

Codec is a pure serialization SPI. Every call goes through:

```java
// consumer code
Codec<Foo> codec = codecFactory.create(Foo.class);
byte[] bytes = codec.encode(foo);  // reflects on Foo via Jackson
```

It's the caller that knows the type `Foo` and must register binding hints for it. Codec itself has no wire types of its own: `JacksonCodec<T>` delegates to the caller-provided `ObjectMapper`, and the `TypeRef<T>` / `JavaType` handling is pure generics — no reflective class lookup happens inside codec.

## When to reconsider

Ship hints from codec itself only if one of these changes:

1. **codec introduces a new transport module that serializes internal metadata** (e.g., an envelope record that wraps user payloads with version/schema info). That envelope would need `BindingReflectionHints`.
2. **codec adds SPI discovery via `ServiceLoader`.** Implementation classes registered under `META-INF/services/` would need reflection and native-image service-loader hints.
3. **codec-protobuf or another variant stores reflection-heavy descriptor classes.** Protobuf in particular may need explicit metadata for generated message classes — evaluate per-module when that variant is included in a native build.

Nothing in `codec-core`, `codec-jackson`, or `codec-gson` triggers these today.

## Verification

Bump the cowork-connector-example to a candidate codec release, build with `mvn -Pnative spring-boot:build-image -DBP_NATIVE_IMAGE=true`, and exercise at least one codec round-trip on a non-trivial user type (mocapi's `McpSession` going through the atom store qualifies). If the round-trip succeeds, codec's contribution is correct.

If a gap surfaces, the error will point to either a consumer-owned type (caller's responsibility) or an internal codec type — only the latter is a codec bug.
