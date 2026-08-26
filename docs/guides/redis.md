# Spring Data Redis

`codec-spring-data-redis` adapts any `Codec<T>` to Spring Data Redis's
`RedisSerializer<T>`, so the codec you built — with its compression and
encryption — is what Redis stores.

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-spring-data-redis</artifactId>
</dependency>
```

## RedisTemplate

```java
RedisTemplate<String, Person> template = new RedisTemplate<>();
template.setConnectionFactory(connectionFactory);
template.setKeySerializer(RedisSerializer.string());
template.setValueSerializer(CodecRedisSerializer.of(codecFactory, Person.class));
```

`CodecRedisSerializer.of` takes a `Codec<T>`, a codec plus its type, or a
`CodecFactory` plus the type. `RedisSerializer`'s contract treats `null` as
absent — it is handed `null` on a miss — so the codec is wrapped with
[`nullSafe()`](composition.md#null-handling); every other value and exception
passes through unchanged.

## The cache abstraction

Spring's cache abstraction wants a serialization pair rather than a serializer:

```java
RedisCacheConfiguration.defaultCacheConfig()
    .serializeValuesWith(CodecRedisSerializer.of(codecFactory, Person.class).serializationPair());
```

### Auto-configuration

With the starter, Spring Boot's cache support, and Spring Data Redis on the
classpath, the auto-configured `CodecFactory` serializes cache values for the
caches you name:

```properties
codec.redis.cache.caches.people=com.example.Person
codec.redis.cache.caches.orders=com.example.OrderSummary
```

Cache values are heterogeneous but a codec is typed, which is why each cache
names its value type — a JSON backend asked to decode to `Object` would hand
back a `Map`, not a `Person`. Caches not listed keep Spring Boot's default
serializer; every other setting (TTL, key prefix, key serializer) is preserved.

A backend whose format is self-describing can serve every cache from one codec.
Apache Fory writes the class into each payload, so with a
[Fory bean](fory.md) in place:

```properties
codec.redis.cache.default-type=java.lang.Object
```

Set `codec.redis.cache.enabled=false` to switch the auto-configuration off.

## Compress, encrypt, cache

The pipeline this was built for — a `CodecFactory` bean whose codecs compress
and encrypt — needs nothing Redis-specific:

```java
@Bean
CodecFactory codecFactory(ObjectMapper mapper, DataKeyProvider keys) {
    CodecFactory json = new JacksonCodecFactory(mapper);
    EnvelopeCodec envelope = EnvelopeCodec.builder(keys).build();
    return new CodecFactory() {
        @Override
        public <T> Codec<T> create(TypeRef<T> type) {
            return json.create(type).andThen(new ZstdCodec()).andThen(envelope);
        }
    };
}
```

Every cache named in `codec.redis.cache.caches` now holds compressed,
authenticated ciphertext.
