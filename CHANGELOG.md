# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[0.2.0]: https://github.com/jwcarman/codec/releases/tag/0.2.0
[0.1.0]: https://github.com/jwcarman/codec/releases/tag/0.1.0
