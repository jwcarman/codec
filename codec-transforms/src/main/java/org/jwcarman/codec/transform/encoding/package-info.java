/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Text-safe encodings: {@code Codec<byte[]>} stages whose output is ASCII-only bytes, for payloads
 * that must live in text columns, JSON strings, URLs or configuration. Put them last in a chain —
 * compress and encrypt first. Decoding is strict: input outside the encoding's alphabet is rejected
 * rather than decoded to garbage.
 */
package org.jwcarman.codec.transform.encoding;
