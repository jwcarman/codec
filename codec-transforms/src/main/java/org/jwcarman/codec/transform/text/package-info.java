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
 * Text as bytes: the {@code Codec<String>} that turns a string into its encoded bytes and back, for
 * payloads that already are text and as a backend-free base for {@code Codec.xmap}. The mirror
 * image of {@code transform.encoding}, which turns bytes into text.
 */
package org.jwcarman.codec.transform.text;
