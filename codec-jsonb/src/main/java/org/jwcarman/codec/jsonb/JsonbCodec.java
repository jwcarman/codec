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

import jakarta.json.JsonException;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.InvalidPayloadException;
import org.jwcarman.codec.spi.InvalidValueException;

/**
 * A codec that serializes a single runtime type through a {@link Jsonb} instance. Encoding writes
 * UTF-8 JSON bytes; decoding reads them back as the codec's type.
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
    try {
      jsonb.toJson(value, type, out);
    } catch (JsonbException | JsonException e) {
      // Yasson wraps a generator failure in JsonbException; Johnzon can raise JSON-P's
      // JsonException from its generator directly.
      throw new InvalidValueException("Unable to encode value as JSON", e);
    }
    return out.toByteArray();
  }

  @Override
  public T decode(byte[] bytes) {
    try {
      return jsonb.fromJson(new ByteArrayInputStream(bytes), type);
    } catch (JsonbException | JsonException e) {
      // Yasson wraps JSON-P's parse failure in JsonbException; Johnzon lets it through as-is.
      throw new InvalidPayloadException("Unable to decode JSON", e);
    }
  }
}
