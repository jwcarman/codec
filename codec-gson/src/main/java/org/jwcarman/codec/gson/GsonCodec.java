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
package org.jwcarman.codec.gson;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;
import org.jwcarman.codec.spi.InvalidValueException;

/**
 * JSON codec backed by {@link Gson}, exchanging UTF-8 bytes.
 *
 * @param <T> the type this codec converts
 */
class GsonCodec<T> implements Codec<T> {

  private final Gson gson;
  private final Type type;

  GsonCodec(Gson gson, Type type) {
    this.gson = gson;
    this.type = type;
  }

  @Override
  public byte[] encode(T value) {
    try {
      return gson.toJson(value, type).getBytes(StandardCharsets.UTF_8);
    } catch (JsonParseException e) {
      throw new InvalidValueException("Unable to encode value as JSON", e);
    }
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return gson.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
    } catch (JsonParseException e) {
      throw new InvalidPayloadException("Unable to decode JSON", e);
    }
  }
}
