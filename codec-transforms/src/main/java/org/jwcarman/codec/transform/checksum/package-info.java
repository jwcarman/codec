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
 * Corruption detection: a {@code Codec<byte[]>} stage that appends a checksum on encode and
 * verifies it on decode. Detects accidental damage — bit rot, truncation, a partial overwrite — not
 * tampering; for integrity against an adversary use the authenticated encryption in {@code
 * codec-crypto}. Compressed frames (gzip, zstd, LZ4) already carry checksums; this is for the
 * payloads that do not.
 */
package org.jwcarman.codec.transform.checksum;
