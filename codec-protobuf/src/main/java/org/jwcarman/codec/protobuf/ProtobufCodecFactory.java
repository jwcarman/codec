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
import com.google.protobuf.Parser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.codec.spi.TypeRef;

public class ProtobufCodecFactory implements CodecFactory {

  @Override
  public <T> Codec<T> create(TypeRef<T> typeRef) {
    Type type = typeRef.getType();
    if (!(type instanceof Class<?> clazz)) {
      throw new IllegalArgumentException(
          "Protobuf codecs do not support parameterized types: " + type);
    }
    if (!GeneratedMessageV3.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException(
          "Type " + clazz.getName() + " is not a GeneratedMessageV3 subclass");
    }
    Class<? extends GeneratedMessageV3> messageType = clazz.asSubclass(GeneratedMessageV3.class);
    Parser<? extends GeneratedMessageV3> parser = getParser(messageType);
    ProtobufCodec<? extends GeneratedMessageV3> codec = new ProtobufCodec<>(parser);
    return (Codec<T>) codec;
  }

  private static Parser<? extends GeneratedMessageV3> getParser(
      Class<? extends GeneratedMessageV3> type) {
    try {
      Method method = type.getMethod("getDefaultInstance");
      GeneratedMessageV3 defaultInstance = (GeneratedMessageV3) method.invoke(null);
      return defaultInstance.getParserForType();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          "No getDefaultInstance() method found on " + type.getName(), e);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Failed to get default instance for " + type.getName(), e);
    }
  }
}
