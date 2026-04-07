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

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Parser;
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
    if (!GeneratedMessage.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException(
          "Type " + clazz.getName() + " is not a GeneratedMessage subclass");
    }
    Class<? extends GeneratedMessage> messageType = clazz.asSubclass(GeneratedMessage.class);
    Parser<? extends GeneratedMessage> parser = getParser(messageType);
    ProtobufCodec<? extends GeneratedMessage> codec = new ProtobufCodec<>(parser);
    return (Codec<T>) codec;
  }

  private static Parser<? extends GeneratedMessage> getParser(
      Class<? extends GeneratedMessage> type) {
    try {
      Method method = type.getMethod("getDefaultInstance");
      GeneratedMessage defaultInstance = (GeneratedMessage) method.invoke(null);
      return defaultInstance.getParserForType();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to get parser for " + type.getName(), e);
    }
  }
}
