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
import com.google.gson.reflect.TypeToken;
import java.nio.charset.StandardCharsets;
import org.jwcarman.codec.spi.Codec;

public class GsonCodec<T> implements Codec<T> {

  private final Gson gson;
  private final TypeToken<T> typeToken;
  private final Class<T> rawType;

  GsonCodec(Gson gson, TypeToken<T> typeToken, Class<T> rawType) {
    this.gson = gson;
    this.typeToken = typeToken;
    this.rawType = rawType;
  }

  @Override
  public byte[] encode(T value) {
    return gson.toJson(value).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public T decode(byte[] bytes) {
    return gson.fromJson(new String(bytes, StandardCharsets.UTF_8), typeToken);
  }

  @Override
  public Class<T> type() {
    return rawType;
  }
}
