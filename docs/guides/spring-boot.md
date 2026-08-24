# Spring Boot

## The starter

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-spring-boot-starter</artifactId>
</dependency>
```

The starter bundles `codec-core` and `codec-autoconfigure`. Add one backend
module alongside it, and a `CodecFactory` bean is registered for that backend.
The backend modules themselves contain no Spring code — all auto-configuration
lives in `codec-autoconfigure`.

## Backend selection

Each backend's auto-configuration activates only when both that backend module
and its underlying library are on the classpath. If several backends are
present, precedence is deterministic:

1. Jackson 3.x (`codec-jackson`)
2. Jackson 2.x (`codec-jackson2`)
3. Gson (`codec-gson`)
4. Protocol Buffers (`codec-protobuf`)

The normal setup is exactly one backend; the ordering just makes the unusual
case (a backend arriving transitively from another library) predictable.

## Overriding

Every auto-configuration backs off if a `CodecFactory` bean already exists, so
defining your own always wins:

```java
@Bean
CodecFactory codecFactory(ObjectMapper mapper) {
    return new JacksonCodecFactory(mapper);
}
```

## Bean reuse

The Jackson and Gson configurations reuse the application's existing
`ObjectMapper` / `Gson` bean when one exists — so your configured modules,
serialization features, and naming strategies apply to codecs too. When no such
bean exists, a default instance is created instead.
