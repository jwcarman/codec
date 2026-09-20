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
 * The Codec API — the entire contract of {@code codec-core}: {@link org.jwcarman.codec.Codec}
 * encodes and decodes one type, {@link org.jwcarman.codec.CodecFactory} produces codecs for any
 * type, and {@link org.jwcarman.codec.TypeRef} captures parameterized types so generics survive
 * erasure. {@code CodecFactory} is the one provider interface — backends implement it; everything
 * else here is what callers use. Transforms are {@code Codec<byte[]>} composed through {@code
 * Codec.andThen}.
 */
package org.jwcarman.codec;
