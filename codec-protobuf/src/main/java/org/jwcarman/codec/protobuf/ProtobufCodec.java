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
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;

/**
 * Codec for a Protocol Buffers message type, encoding with the message's wire format and decoding
 * with its {@link Parser}.
 *
 * @param <T> the message type this codec converts
 */
class ProtobufCodec<T> implements Codec<T> {

  private final Parser<? extends GeneratedMessage> parser;
  private final Class<T> type;

  ProtobufCodec(Parser<? extends GeneratedMessage> parser, Class<T> type) {
    this.parser = parser;
    this.type = type;
  }

  @Override
  public byte[] encode(T value) {
    // type was verified to be a GeneratedMessage subclass at creation, so this cast is checked
    // and cannot fail for a T.
    return GeneratedMessage.class.cast(value).toByteArray();
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return type.cast(parser.parseFrom(bytes));
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidPayloadException("Failed to decode protobuf message", e);
    }
  }
}
