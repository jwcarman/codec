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
 * Spring Boot auto-configuration for the backends. Exactly one {@code CodecFactory} bean is
 * registered, chosen by precedence — an application-defined {@code ThreadSafeFory} bean first, then
 * Jackson 3, Jackson 2, Gson, JSON-B and Protocol Buffers by classpath presence — and a
 * user-defined {@code CodecFactory} bean always wins.
 */
package org.jwcarman.codec.autoconfigure;
