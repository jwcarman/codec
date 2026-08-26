# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Breaking changes
- The built-in transforms moved out of `codec-core` into a new
  `codec-transforms` module, and into packages by kind:
  `org.jwcarman.codec.transform.compress` (`CompressionStreamCodec`,
  `GzipCodec`, `DeflateCodec`) and `org.jwcarman.codec.transform.encoding`
  (`Base64Codec`). `codec-core` is now the SPI alone. Migration: add
  `codec-transforms` (the Spring Boot starter already includes it) and update
  the imports. `codec-zstd` now depends on `codec-transforms`.

### Added
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

[0.6.0]: https://github.com/jwcarman/codec/releases/tag/0.6.0
[0.5.0]: https://github.com/jwcarman/codec/releases/tag/0.5.0
[0.4.0]: https://github.com/jwcarman/codec/releases/tag/0.4.0
[0.3.0]: https://github.com/jwcarman/codec/releases/tag/0.3.0
[0.2.0]: https://github.com/jwcarman/codec/releases/tag/0.2.0
[0.1.0]: https://github.com/jwcarman/codec/releases/tag/0.1.0
