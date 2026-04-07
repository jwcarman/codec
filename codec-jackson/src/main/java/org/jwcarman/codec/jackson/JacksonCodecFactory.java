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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.codec.spi.TypeRef;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

public class JacksonCodecFactory implements CodecFactory {

  private final ObjectMapper objectMapper;

  public JacksonCodecFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public <T> Codec<T> create(Class<T> type) {
    JavaType javaType = objectMapper.getTypeFactory().constructType(type);
    return new JacksonCodec<>(objectMapper, javaType, type);
  }

  @Override
  public <T> Codec<T> create(TypeRef<T> typeRef) {
    JavaType javaType = objectMapper.getTypeFactory().constructType(typeRef.getType());
    Class<T> rawType = extractRawType(typeRef.getType());
    return new JacksonCodec<>(objectMapper, javaType, rawType);
  }

  private static <T> Class<T> extractRawType(Type type) {
    if (type instanceof ParameterizedType parameterizedType) {
      return asClass(parameterizedType.getRawType());
    }
    return asClass(type);
  }

  private static <T> Class<T> asClass(Type type) {
    return (Class<T>) type;
  }
}
