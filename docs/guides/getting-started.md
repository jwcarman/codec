# Getting Started

## Requirements

- Java 25+
- Spring Boot 4.x (only if you use the starter — the backends themselves are
  Spring-free)

## Add the dependencies

Import the BOM for version alignment:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.jwcarman.codec</groupId>
            <artifactId>codec-bom</artifactId>
            <version>0.6.0</version>
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

## Use it

Inject the auto-configured `CodecFactory` and create codecs for your types:

```java
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

## Without Spring

Skip the starter and construct the factory directly:

```java
CodecFactory factory = new JacksonCodecFactory(objectMapper);
Codec<Person> codec = factory.create(Person.class);
```

Every backend factory has a public constructor taking its underlying library's
entry point (`ObjectMapper`, `Gson`, `Jsonb`) — or none at all
(`ProtobufCodecFactory`).
