# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Core SPI: `Codec<T>`, `CodecFactory`, and `TypeRef<T>` type token
- Jackson backend (`codec-jackson`)
- Gson backend (`codec-gson`)
- Protocol Buffers backend (`codec-protobuf`)
- Spring Boot auto-configuration for all backends
- BOM module (`codec-bom`) for version alignment
