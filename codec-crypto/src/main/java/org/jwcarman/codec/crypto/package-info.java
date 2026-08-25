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
 * Envelope encryption as a {@code Codec<byte[]>} transform: AES-256-GCM over a per-message data key
 * that is itself wrapped by a key-encryption key reached through the {@link
 * org.jwcarman.codec.crypto.DataKeyProvider} SPI (in-process JCE or a remote KMS). {@link
 * org.jwcarman.codec.crypto.EnvelopeCodec} owns the versioned wire format; {@link
 * org.jwcarman.codec.crypto.DataKeyStrategy} decides data-key lifecycle. Read the security contract
 * on {@code DataKeyProvider} before implementing one, and the published threat model before storing
 * encrypted data.
 */
package org.jwcarman.codec.crypto;
