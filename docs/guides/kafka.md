# Apache Kafka

`codec-kafka` adapts any `Codec<T>` to Kafka's `Serializer`, `Deserializer`,
and `Serde`, so the codec you built — with its compression and encryption —
is what goes on the wire.

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-kafka</artifactId>
</dependency>
```

## Producers and consumers

```java
Map<String, Object> config = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
Codec<Person> codec = codecFactory.create(Person.class);

Producer<String, Person> producer =
    new KafkaProducer<>(config, new StringSerializer(), new CodecSerializer<>(codec));
Consumer<String, Person> consumer =
    new KafkaConsumer<>(config, new StringDeserializer(), new CodecDeserializer<>(codec));
```

With Spring Kafka, hand the instances to the factories:

```java
@Bean
ProducerFactory<String, Person> producerFactory(
        KafkaProperties properties, CodecFactory codecFactory) {
    return new DefaultKafkaProducerFactory<>(
        properties.buildProducerProperties(),
        new StringSerializer(),
        new CodecSerializer<>(codecFactory.create(Person.class)));
}
```

Kafka's reflective configuration path (`value.serializer=...`) needs a
public no-arg constructor and can't build a codec-backed serializer; the
adapters are meant to be instantiated in code.

The topic is ignored — a codec doesn't vary by topic — and `null` maps to
`null` in both directions, which is Kafka's tombstone, through
[`nullSafe()`](composition.md#null-handling). Everything else, including any
exception the codec throws, passes through unchanged. `KafkaConsumer.poll()`
surfaces it as `RecordDeserializationException` with the `CodecException` as
the cause; with Spring Kafka, wrap the deserializer in
`ErrorHandlingDeserializer` so a `DefaultErrorHandler` can dead-letter on
`InvalidPayloadException` and retry on `TransientCodecException`.

## Kafka Streams

```java
KStream<String, Person> people =
    builder.stream("people", Consumed.with(Serdes.String(), new CodecSerde<>(codec)));
```

## Compress, encrypt, produce

A codec that compresses and encrypts needs nothing Kafka-specific:

```java
Codec<Person> codec =
    codecFactory.create(Person.class).andThen(new Lz4Codec()).andThen(envelope);
```

Kafka can compress batches itself (`compression.type`), which is usually the
better place for compression on a busy topic; a codec-level transform is for
when the payload must be compressed or encrypted *as a unit* — end-to-end
encryption being the typical reason.

## Where next

- [Handling Failures](error-handling.md#in-practice) — dead-lettering,
  parking, and retrying around `codec.decode` in a consumer
- [Codec Composition](composition.md) — the `andThen` chain a Kafka
  `Serializer`/`Deserializer` pair carries unchanged
- [Encryption](encryption.md) — `EnvelopeCodec`, used above for end-to-end
  encryption
