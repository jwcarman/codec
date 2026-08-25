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
package org.jwcarman.codec.jsonb;

import jakarta.json.bind.Jsonb;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import org.jwcarman.codec.spi.Codec;

/**
 * A codec that serializes a single runtime type through a {@link Jsonb} instance. Encoding writes
 * UTF-8 JSON bytes; decoding reads them back as the codec's type. Failures surface as JSON-B's own
 * {@link jakarta.json.bind.JsonbException}.
 */
class JsonbCodec<T> implements Codec<T> {

  private final Jsonb jsonb;
  private final Type type;

  JsonbCodec(Jsonb jsonb, Type type) {
    this.jsonb = jsonb;
    this.type = type;
  }

  @Override
  public byte[] encode(T value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    jsonb.toJson(value, type, out);
    return out.toByteArray();
  }

  @Override
  public T decode(byte[] bytes) {
    return jsonb.fromJson(new ByteArrayInputStream(bytes), type);
  }
}
