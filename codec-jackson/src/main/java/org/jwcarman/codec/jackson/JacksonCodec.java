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
package org.jwcarman.codec.jackson;

import org.jwcarman.codec.spi.Codec;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

public class JacksonCodec<T> implements Codec<T> {

  private final ObjectMapper objectMapper;
  private final JavaType javaType;
  private final Class<T> rawType;

  JacksonCodec(ObjectMapper objectMapper, JavaType javaType, Class<T> rawType) {
    this.objectMapper = objectMapper;
    this.javaType = javaType;
    this.rawType = rawType;
  }

  @Override
  public byte[] encode(T value) {
    return objectMapper.writeValueAsBytes(value);
  }

  @Override
  public T decode(byte[] bytes) {
    return objectMapper.readValue(bytes, javaType);
  }

  @Override
  public Class<T> type() {
    return rawType;
  }
}
