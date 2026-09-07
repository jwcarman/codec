# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.9.0] - 2026-09-07

### Added
- `TypeRef.listOf`, `setOf`, `optionalOf`, `mapOf` and `parameterized` build a
  parameterized type reference from the references of its arguments, so generic
  code can create a `Codec<List<O>>` or `Codec<Envelope<O>>` from a caller's
  `TypeRef<O>`. A built reference equals the same type captured by an anonymous
  subclass. `parameterized` takes the class as `Class<? super T>`, so the
  compiler rejects a class that is not the declared type's; a class with no type
  parameters, a wrong argument count and primitive arguments are rejected at
  construction. The argument list against the declared type's own arguments is
  the caller's to get right

### Changed
- `codec-spring-boot-starter` now brings `codec-versioned` along with
  `codec-core`, `codec-transforms` and `codec-autoconfigure`: the module has
  no dependencies of its own, and versioned storage is part of the toolkit the
  starter promises

### Fixed
- `codec-kafka` no longer resolves the `lz4-java` 1.10.1 and `zstd-jni` 1.5.6 that
  `kafka-clients` declares: the parent now manages both at the versions codec
  ships (1.11.2 and 1.5.7), which closes GHSA-xx22-p4ch-683r (CVE-2026-59949, a
  JVM crash in lz4-java's native XXHash on invalid array ranges) on that path

## [0.8.0] - 2026-09-07

### Breaking changes
- Every failure `Codec.encode` and `Codec.decode` report is now a
  `CodecException` from `codec-core`, in one of four families keyed to what the
  caller does next: `InvalidValueException` (fix the value),
  `InvalidPayloadException` (quarantine), `UnsupportedFormatException` (hold for
  a newer reader), `TransientCodecException` (retry). Anything catching
  `IllegalArgumentException`, `IllegalStateException`, `UncheckedIOException`,
  `ClassCastException`, or a Jackson/Gson/JSON-B/Fory/protobuf exception from
  `encode`/`decode` must catch the family (or `CodecException`) instead; the
  library's exception is still there as the cause. Construction-time exceptions
  are unchanged. `codec-crypto`'s `DecryptionException`, `KeyAccessException`
  and `EncryptionException` keep their names and messages and now extend
  `InvalidPayloadException`, `TransientCodecException` and
  `TransientCodecException` respectively; an unknown envelope version or
  algorithm id is now `UnsupportedFormatException`, not a rejection.
  `codec-versioned`'s `UnknownVersionException` now extends
  `UnsupportedFormatException` and is no longer a `VersionedFormatException`.
  See the new [Handling failures](https://jwcarman.github.io/codec/guides/error-handling/)
  guide.
- `Base32Codec.standard()` and `.hex()` decode canonically: upper case only, and
  non-zero trailing bits in the final symbol are rejected (`MZ======` is an
  error; `MY======` is `f`). Callers who fed lower-case input opt back in with
  `.caseInsensitive()`

### Changed
- The build compiles with `-Xlint:all,-processing,-unchecked -Werror`: any javac
  warning in an enabled category fails compilation. `unchecked` is off because
  `TypeRef.rawClass()` is the one unchecked cast the type system cannot express;
  no other is permitted. The Gson, Protobuf and Fory backends no longer carry
  unchecked casts, and every exception class declares a `serialVersionUID`
- `codec-gson` encodes by the codec's declared type (`Gson.toJson(value, type)`)
  rather than the value's runtime class, matching what decode already did; a
  polymorphic value now serialises as its declared `T`
- `codec-fory`: Apache Fory 1.1.0 → 1.7.1. `ForyCodecFactory.of(...)` takes Fory's
  defaults, and Fory's default changed from schema-consistent to compatible mode
  in 1.2.0 (without a release note), so payloads now carry their class schema:
  a reader whose class has gained or lost a field decodes correctly where
  schema-consistent mode silently returned a mis-shifted object. It costs a few
  bytes per class per message — a lone four-field record roughly doubles, a
  100-item order grows 2%; throughput is unchanged. Payloads written by earlier
  releases still decode, but bytes written by this release cannot be read by a
  schema-consistent Fory. Callers who want a specific mode build their own
  `ThreadSafeFory` and pass it to the constructor. Per Fory's security guidance
  the helper also sets `deserializeUnknownClass(false)`, so a payload naming an
  unregistered class is rejected rather than materialised as an anonymous struct
- `JacksonCodecFactory`, `Jackson2CodecFactory`, and `GsonCodecFactory` now reject a
  `null` engine or `TypeRef` with a labelled `NullPointerException`, matching the
  JSON-B and Fory factories; `CompressionStreamCodec` likewise rejects `null` on
  `encode` and `decode` before touching a stream, matching the other transforms

### Added
- `TypeRef.rawClass()`: the erased class of the captured type, typed `Class<T>`,
  so a backend can use the checked `Class.cast` instead of an unchecked cast of
  its own. It is the single unchecked cast in the codebase and its Javadoc says so
- `codec-versioned`: `VersionedCodec` prefixes each payload with a magic and
  version header and dispatches decoding on it, so a storage strategy can change
  without a flag day — old versions stay registered and readable while an
  explicit write version makes a two-phase rollout possible
- `codec-benchmarks`: JMH benchmarks for every compression transform (deflate
  and zstd at several levels), encoding, backend (a small record and a
  100-item object graph), and the envelope-encryption strategies (not
  published), with the results and a benchmarks page in the docs
- `Base32Codec.of(alphabet)` and `of(alphabet, pad)`: a strict codec over any
  32-symbol ASCII alphabet, with `caseInsensitive()` and `aliasing(...)` as
  decode-side opt-ins; `crockford()` (ULIDs), `zBase32()` and `geohash()`
  presets. The encode and decode loops are specialised for whole groups, about
  4x the throughput of the previous implementation

### Documentation
- Fixed a newcomer-walkthrough review's findings: the Composition guide's
  chaining sample and the `Codec.andThen` Javadoc snippet now build
  `EnvelopeCodec` (there is no `AesCodec`); the landing page states the Java
  25 / Spring Boot 4.x requirement; the landing page and Getting Started link
  Apache Kafka and Spring Data Redis; the Guides nav orders Handling Failures
  before Evolving Stored Formats; the Kafka guide covers
  `RecordDeserializationException`/`ErrorHandlingDeserializer` and declares
  its `config` variable; the Spring Boot guide states what happens with no
  backend on the classpath; and the Redis guide turns its auto-configuration
  prerequisites into a checklist and adds a `codec.redis.cache.*` properties
  table
- Benchmarks re-run on 2026-09-06 with Fory 1.7.1, zstd-jni 1.5.7-15, and
  lz4-java 1.11.2: zstd decodes 1 MB payloads 18–29% faster at every level, the
  backend numbers and encoded sizes reflect Fory's compatible mode, and the Fory
  guide gains a schema-evolution section
- Compression claims corrected against the benchmarks: the JDK codecs are
  fastest on payloads under a few hundred bytes, zstd's default level is not
  smaller than gzip on prose, and LZ4-HC is a decode-side optimisation
- New guide, [Evolving Stored Formats](https://jwcarman.github.io/codec/guides/versioned/):
  how `codec-versioned` works, a worked upgrade, the fleet rollout order, and
  what belongs inside the versioned codec versus outside it
- The aggregated Javadoc for every module is published with the site at
  [jwcarman.github.io/codec/api](https://jwcarman.github.io/codec/api/), one set
  per release, built from the release tag

## [0.7.0] - 2026-08-26

### Breaking changes
- The built-in transforms moved out of `codec-core` into a new
  `codec-transforms` module, and into packages by kind:
  `org.jwcarman.codec.transform.compress` (`CompressionStreamCodec`,
  `GzipCodec`, `DeflateCodec`) and `org.jwcarman.codec.transform.encoding`
  (`Base64Codec`). `codec-core` is now the SPI alone. Migration: add
  `codec-transforms` (the Spring Boot starter already includes it) and update
  the imports. `codec-zstd` now depends on `codec-transforms`.

### Added
- `codec-kafka`: `CodecSerializer`, `CodecDeserializer`, and `CodecSerde` adapt
  any codec to Kafka's client serialization interfaces; tombstones map to
  `null` in both directions
- `codec-spring-data-redis`: `CodecRedisSerializer` adapts any codec to Spring
  Data Redis's `RedisSerializer` (and, via `serializationPair()`, the cache
  abstraction); Spring Boot auto-configuration serializes the cache values named
  in `codec.redis.cache.caches` through the application's `CodecFactory`
- `Codec.nullSafe()`: an explicit wrapper that maps `null` to `null` in both
  directions for integrations whose contract treats `null` as absent
- `Codec.xmap(forward, backward)`: derive a codec for another type from an
  existing one — the domain-type-versus-wire-type tool for backends that only
  serialize generated or registered classes
- `StringCodec` in `codec-transforms` (`transform.text`): text as its own bytes
  in any charset, with strict decoding
- `ChecksumCodec` in `codec-transforms` (`transform.checksum`): a 32-bit
  checksum trailer for corruption detection, CRC-32C by default
- `HexCodec` in `codec-transforms` (RFC 4648 base16): lower- or upper-case
  output, strict case-insensitive decoding
- `Base32Codec` in `codec-transforms` (RFC 4648 §6 Base32 and §7 base32hex),
  strict case-insensitive decoding
- `codec-lz4`: LZ4 frame-format compression transform (`Lz4Codec`) with fast and
  high-compression (LZ4-HC) modes and the same decompression-bomb cap as the
  built-in transforms

## [0.6.0] - 2026-08-25

### Added
- `codec-fory`: Apache Fory backend (`ForyCodecFactory`) for fast JVM-only
  binary serialization; class registration is mandatory and never relaxed by
  the module. Spring Boot auto-configuration activates only when the application
  defines a `ThreadSafeFory` bean, and then takes precedence over every
  classpath-detected backend
- `codec-jsonb`: Jakarta JSON Binding backend (`JsonbCodecFactory`) with Spring
  Boot auto-configuration; backend precedence is now Jackson 3 → Jackson 2 →
  Gson → JSON-B → Protobuf
- `codec-zstd`: Zstandard compression transform (`ZstdCodec`) with configurable
  level and the same decompression-bomb cap as the built-in transforms
- `Base64Codec` in `codec-core`: a text-safe transform (basic, URL-safe, URL-safe
  without padding, MIME) for chains whose output must live in text columns,
  JSON strings, or URLs
- Tests proving CBOR, Smile, YAML, and XML work as Jackson backends by mapper
  swap, and a Jackson Dataformats guide documenting it

## [0.5.0] - 2026-08-25

### Added
- `codec-crypto`: AES-256-GCM envelope-encryption `Codec<byte[]>` transform with
  pluggable key management (`DataKeyProvider` SPI — in-process JCE or remote
  KMS), fresh-DEK-per-message default with opt-in bounded caching, and a
  versioned self-describing wire format
- `codec-crypto`: optional `java.security.Provider` injection on
  `EnvelopeCodec.Builder` and `JceDataKeyProvider.Builder`, resolved and
  fail-fast checked at build time, so a FIPS-validated provider can be pinned
  per instance instead of installed globally
- `codec-crypto`: `JceDataKeyProvider` wrapped-DEK blobs now carry a
  wrap-scheme tag (`[scheme:1][payload]`, scheme `0x01` = AES-KW/RFC 3394),
  giving the zero-dependency provider its own wrap-algorithm migration story
- `codec-crypto`: assurance program — known-answer tests against NIST CAVP
  GCM vectors and the RFC 3394 wrap vector, Jazzer decoder fuzz targets with a
  committed seed corpus and a `-Pfuzz` live-fuzzing profile, PIT mutation
  testing and SpotBugs+findsecbugs static analysis in the `ci` profile, and a
  published [threat model](https://jwcarman.github.io/codec/guides/threat-model/)

## [0.4.0] - 2026-08-24

### Breaking changes
- `codec-bom` no longer inherits from `codec-parent`. Because the parent imports
  `spring-boot-dependencies` for its own build, the BOM was re-exporting all of
  Spring Boot's dependency management — 1921 managed entries, of which only 7
  were Codec's own — to every consumer that imported it, silently overriding
  their own versions for JUnit, AssertJ, Jackson, Guava and more. The BOM now
  manages exactly the seven Codec artifacts and nothing else. Consumers who were
  unknowingly relying on a leaked version must now pin it themselves.

### Fixed
- `codec-core`'s published description no longer claims auto-configuration,
  which moved to `codec-autoconfigure` in 0.2.0

## [0.3.0] - 2026-08-24

### Breaking changes
- Removed unused dependencies from the published compile surface. The parent
  declared `slf4j-api` in `<dependencies>`, so it landed on every artifact
  including `codec-bom`; `codec-core` additionally carried
  `spring-boot-autoconfigure`, left over from before auto-configuration moved
  to `codec-autoconfigure`. Neither was used: nothing in the codebase logs, and
  `codec-core` has no Spring imports. **`codec-core` now publishes with zero
  transitive dependencies.** Consumers who were relying on either transitive
  must now declare it directly.

### Changed
- `codec-jackson2` declares `jackson-core` explicitly rather than inheriting it
  transitively from `jackson-databind`
- `codec-autoconfigure` declares `spring-context` and `spring-beans`, which it
  compiles against, plus the four engine jars it references for
  `@ConditionalOnClass` as optional dependencies; it previously relied on
  transitives of optional dependencies
- `protobuf.version` moved to the parent pom alongside every other version
  property

### Added
- CI-only dependency hygiene gates in the `ci` profile:
  `dependency:analyze-only` with `failOnWarning`, and `maven-enforcer-plugin`
  with `dependencyConvergence`, `requireUpperBoundDeps`,
  `banDuplicatePomDependencyVersions`, and `banDynamicVersions`. The default
  build is unaffected.

### Requirements
- Spring Boot 4.1.1 (from 4.0.5)
- protobuf-java 4.36.0 (from 4.35.0)

### Documentation
- MkDocs Material documentation site deployed to GitHub Pages, linked from the
  README

## [0.2.0] - 2026-08-23

### Breaking changes
- Spring Boot auto-configuration moved out of the backend modules into the new
  `codec-autoconfigure` module. Spring Boot applications should now depend on
  `codec-spring-boot-starter` plus a backend module; the backend modules
  themselves are Spring-free and no longer register anything on their own.
- Backend codec implementation classes (`JacksonCodec`, `GsonCodec`,
  `ProtobufCodec`) are now package-private. Interact with codecs through the
  `Codec<T>` interface returned by the factories.
- Removed the empty `*CodecProperties` configuration records (`codec.jackson`,
  `codec.gson`, `codec.protobuf` prefixes had no properties).

### Added
- Codec composition: `Codec.andThen(Codec<byte[]>)` layers byte-level
  transforms (compression, encryption, ...) onto any codec, unwinding them
  automatically in reverse order on decode
- `CompressionStreamCodec` base class for stream-based compression transforms,
  with built-in protection against decompression bombs (64 MiB default cap,
  tunable per instance)
- `GzipCodec` (gzip/RFC 1952 framing) and `DeflateCodec` (zlib/RFC 1950
  framing with configurable compression level) built-in transforms
- Jackson 2.x backend (`codec-jackson2`) for projects still on
  `com.fasterxml.jackson`
- `codec-autoconfigure` module with deterministic backend precedence when
  multiple backends are present (Jackson 3 → Jackson 2 → Gson → Protobuf);
  a user-defined `CodecFactory` bean always wins
- `codec-spring-boot-starter` bundling `codec-core` and `codec-autoconfigure`
- `Automatic-Module-Name` manifest entries (`org.jwcarman.codec.*`) in all jars

### Changed
- The parent pom no longer inherits `spring-boot-starter-parent`; Spring Boot
  versions are managed via a `spring-boot-dependencies` BOM import and build
  plugin versions are pinned explicitly

### Documentation
- Javadoc across the public API
- README coverage of composition, the built-in compression transforms, the
  Jackson 2 backend, and the starter-based auto-configuration

## [0.1.0] - 2026-04-07

### Added
- Core SPI: `Codec<T>`, `CodecFactory`, and `TypeRef<T>` super type token
- `TypeRef<T>` with `of(Class<T>)` factory, proper `equals()`/`hashCode()`, and `toString()`
- `CodecFactory` with single abstract `create(TypeRef<T>)` and default `create(Class<T>)` sugar
- Jackson backend (`codec-jackson`) using `ObjectMapper` for JSON serialization
- Gson backend (`codec-gson`) using `Gson` for JSON serialization
- Protocol Buffers backend (`codec-protobuf`) using idiomatic `Parser<T>` API (Protobuf 4.x)
- Spring Boot auto-configuration for all backends
- BOM module (`codec-bom`) for version alignment
- GitHub Actions CI with SonarCloud analysis
- Maven Central publishing workflow
- Dependabot for automated dependency updates

[0.9.0]: https://github.com/jwcarman/codec/releases/tag/0.9.0
[0.8.0]: https://github.com/jwcarman/codec/releases/tag/0.8.0
[0.7.0]: https://github.com/jwcarman/codec/releases/tag/0.7.0
[0.6.0]: https://github.com/jwcarman/codec/releases/tag/0.6.0
[0.5.0]: https://github.com/jwcarman/codec/releases/tag/0.5.0
[0.4.0]: https://github.com/jwcarman/codec/releases/tag/0.4.0
[0.3.0]: https://github.com/jwcarman/codec/releases/tag/0.3.0
[0.2.0]: https://github.com/jwcarman/codec/releases/tag/0.2.0
[0.1.0]: https://github.com/jwcarman/codec/releases/tag/0.1.0
