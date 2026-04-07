# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[0.1.0]: https://github.com/jwcarman/codec/releases/tag/0.1.0
