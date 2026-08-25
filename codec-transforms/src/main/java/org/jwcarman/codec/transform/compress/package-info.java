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
 * Compression transforms: {@code Codec<byte[]>} stages that shrink a payload on the way out and
 * inflate it on the way back, for use with {@code Codec.andThen}. All of them cap the decoded size
 * (64 MiB by default) so a hostile payload cannot exhaust memory on decode.
 *
 * <p>{@link org.jwcarman.codec.transform.compress.CompressionStreamCodec} is the base for
 * stream-backed implementations; gzip and deflate ship here, and Zstandard in {@code codec-zstd}.
 */
package org.jwcarman.codec.transform.compress;
