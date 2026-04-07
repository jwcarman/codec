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
package org.jwcarman.codec.protobuf;

import com.google.protobuf.GeneratedMessageV3;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.codec.spi.TypeRef;

public class ProtobufCodecFactory implements CodecFactory {

  @Override
  public <T> Codec<T> create(Class<T> type) {
    if (!GeneratedMessageV3.class.isAssignableFrom(type)) {
      throw new IllegalArgumentException(
          "Type " + type.getName() + " is not a GeneratedMessageV3 subclass");
    }
    Class<? extends GeneratedMessageV3> messageType = type.asSubclass(GeneratedMessageV3.class);
    Method parseFromMethod = findParseFromMethod(messageType);
    ProtobufCodec<? extends GeneratedMessageV3> codec =
        new ProtobufCodec<>(messageType, parseFromMethod);
    return (Codec<T>) codec;
  }

  @Override
  public <T> Codec<T> create(TypeRef<T> typeRef) {
    Class<T> rawType = extractRawType(typeRef.getType());
    return create(rawType);
  }

  private static Method findParseFromMethod(Class<? extends GeneratedMessageV3> type) {
    try {
      return type.getMethod("parseFrom", byte[].class);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("No parseFrom(byte[]) method found on " + type.getName(), e);
    }
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
