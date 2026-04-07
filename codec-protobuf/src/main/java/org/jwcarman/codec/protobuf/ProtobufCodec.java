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
import com.google.protobuf.InvalidProtocolBufferException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jwcarman.codec.spi.Codec;

class ProtobufCodec<T extends GeneratedMessageV3> implements Codec<T> {

  private final Class<T> type;
  private final Method parseFromMethod;

  ProtobufCodec(Class<T> type, Method parseFromMethod) {
    this.type = type;
    this.parseFromMethod = parseFromMethod;
  }

  @Override
  public byte[] encode(T value) {
    return value.toByteArray();
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return type.cast(parseFromMethod.invoke(null, bytes));
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof InvalidProtocolBufferException) {
        throw new IllegalArgumentException("Failed to decode protobuf message", e.getCause());
      }
      throw new IllegalStateException("Failed to invoke parseFrom", e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Failed to invoke parseFrom", e);
    }
  }

  @Override
  public Class<T> type() {
    return type;
  }
}
