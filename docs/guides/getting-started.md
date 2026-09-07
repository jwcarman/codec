# Getting Started

## Requirements

- Java 25+
- Spring Boot 4.x (only if you use the starter — the backends themselves are
  Spring-free)

## Add the dependencies

!!! note "Version"
    The dependency snippets name the latest release. This site tracks `main`;
    anything the [Changelog](https://github.com/jwcarman/codec/blob/main/CHANGELOG.md)
    lists under *Unreleased* is not in that release yet.

Import the BOM for version alignment:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.jwcarman.codec</groupId>
            <artifactId>codec-bom</artifactId>
            <version>0.9.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

For Spring Boot applications, add the starter plus the backend you want:

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-spring-boot-starter</artifactId>
</dependency>

<!-- pick one backend -->
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-jackson</artifactId>
</dependency>
```

Backend choices: `codec-jackson` (Jackson 3.x), `codec-jackson2` (Jackson 2.x),
`codec-gson`, `codec-jsonb`, `codec-protobuf`, and `codec-fory` (JVM-only binary,
see [Apache Fory](fory.md)). The Jackson backends also cover
CBOR, Smile, YAML, and XML — see [Jackson Dataformats](dataformats.md).

### Which backend?

| You have | Add | Because |
|---|---|---|
| No preference | `codec-jackson` | JSON, the most widely understood format; Jackson 3 is the classpath-detected default when several backends are present |
| An app already on Jackson 2 (`com.fasterxml`) | `codec-jackson2` | Reuses your `ObjectMapper` bean |
| Gson or JSON-B already in the app | `codec-gson` / `codec-jsonb` | Reuses the bean you have |
| `.proto` contracts shared with other systems | `codec-protobuf` | Generated messages are the type |
| JVM on both ends, speed and size matter | `codec-fory` | 10-15× faster than the JSON backends on a real object graph ([benchmarks](../benchmarks.md)); class registration required |

`codec-jsonb` depends on the Jakarta JSON Binding API only; bring a provider,
e.g. [Eclipse Yasson](https://github.com/eclipse-ee4j/yasson) (the reference
implementation Codec is tested against; Apache Johnzon also works):

```xml
<dependency>
    <groupId>org.eclipse</groupId>
    <artifactId>yasson</artifactId>
</dependency>
```

Spring Boot's BOM manages Yasson's version; without it, pick the version from
Maven Central — `codec-bom` manages only codec's own artifacts. Either way,
malformed input surfaces as `InvalidPayloadException` with the provider's own
exception as the cause — see [Handling Failures](error-handling.md).

## Use it

Inject the auto-configured `CodecFactory` and create codecs for your types:

```java
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;

@Service
public class PersonStore {

    private final Codec<Person> codec;

    public PersonStore(CodecFactory codecFactory) {
        this.codec = codecFactory.create(Person.class);
    }

    public byte[] serialize(Person person) {
        return codec.encode(person);
    }

    public Person deserialize(byte[] bytes) {
        return codec.decode(bytes);
    }
}
```

## Generic types

Java erases generics at runtime, so `create(List.class)` cannot know it holds
`Person`. `TypeRef` captures the full type through an anonymous subclass:

```java
Codec<List<Person>> codec = codecFactory.create(new TypeRef<List<Person>>() {});
Codec<Map<String, Integer>> mapCodec =
    codecFactory.create(new TypeRef<Map<String, Integer>>() {});
```

`TypeRef` implements `equals` and `hashCode` on the captured type, so it is safe
to use as a cache key.

An anonymous subclass only works where the type is spelled out. Inside generic
code, where the element type is the caller's, build the reference from the
reference you were given:

```java
<O> Codec<List<O>> batchCodec(TypeRef<O> element) {
    return codecFactory.create(TypeRef.listOf(element));
}
```

`listOf`, `setOf`, `optionalOf` and `mapOf` cover the JDK collections and nest
to any depth. For a generic class of your own, `parameterized` takes the class
and one reference per type parameter; the compiler checks that the class you
name is the one in the declared type, and the argument count is checked when
the reference is built:

```java
record Envelope<O>(String id, O payload) {}

<O> Codec<Envelope<O>> envelopeCodec(TypeRef<O> element) {
    return codecFactory.create(TypeRef.parameterized(Envelope.class, element));
}
```

A built reference equals the same type captured by an anonymous subclass, so a
factory cache sees one type, not two. What the compiler cannot check is that the
arguments match the declared type's own, in identity and order, or a declared
type more specific than the class you name (`TypeRef<LinkedList<O>>` from
`List.class`). Such a mismatch does not fail inside the codec: decode succeeds
with a value of the built type, and the `ClassCastException` appears where that
value is first used. Round-trip a `parameterized` reference once in a test.

## Without Spring

Skip the starter and add a backend module directly — no `codec-spring-boot-starter`,
no auto-configuration:

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-jackson</artifactId>
</dependency>
```

Add `codec-transforms` next to it if you want compression, encoding, or
checksum transforms — the starter pulls it in for you, but a plain-Java build
adds it explicitly (see [Codec Composition](composition.md#the-transforms-module)):

```xml
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-transforms</artifactId>
</dependency>
```

Then construct the factory directly:

```java
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import tools.jackson.databind.json.JsonMapper;

CodecFactory factory = new JacksonCodecFactory(JsonMapper.builder().build());
Codec<Person> codec = factory.create(Person.class);
```

Every backend factory has a public constructor taking its underlying library's
entry point (`ObjectMapper`, `Gson`, `Jsonb`), a caller-built `ThreadSafeFory`
(`ForyCodecFactory`, or the `ForyCodecFactory.of(...)` helper — see
[Apache Fory](fory.md)) — or none at all (`ProtobufCodecFactory`).

## Where next

- [Codec Composition](composition.md) — compression, encryption, and custom
  transforms
- [Handling Failures](error-handling.md) — the four exception families and
  what to do with each
- [Spring Boot](spring-boot.md) — auto-configuration, backend precedence, and
  what the starter does not include
- [Encryption](encryption.md) — envelope encryption and key management
- [Apache Kafka](kafka.md) — `Serializer`/`Deserializer`/`Serde` adapters
- [Spring Data Redis](redis.md) — `RedisSerializer` and cache
  auto-configuration
- [API reference](https://jwcarman.github.io/codec/api/latest/) — the Javadoc
  for every module, one set per release
