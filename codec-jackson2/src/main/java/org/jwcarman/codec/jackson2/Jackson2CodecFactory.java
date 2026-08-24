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
package org.jwcarman.codec.jackson2;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.codec.spi.TypeRef;

/** Produces JSON codecs backed by a shared Jackson 2.x {@link ObjectMapper}. */
public class Jackson2CodecFactory implements CodecFactory {

  private final ObjectMapper objectMapper;

  /**
   * Creates a factory that uses the given mapper for all codecs it produces.
   *
   * @param objectMapper the mapper to serialize and deserialize with
   */
  public Jackson2CodecFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public <T> Codec<T> create(TypeRef<T> typeRef) {
    JavaType javaType = objectMapper.getTypeFactory().constructType(typeRef.getType());
    return new Jackson2Codec<>(objectMapper, javaType);
  }
}
