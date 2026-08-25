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
import java.util.Objects;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.codec.spi.TypeRef;

/**
 * A {@link CodecFactory} backed by Jakarta JSON Binding (JSON-B). Bring any {@link Jsonb} instance
 * — Yasson at runtime, or whichever implementation the platform provides — and every codec this
 * factory creates serializes through it, honoring its configuration and the {@code
 * jakarta.json.bind.annotation} annotations on your types.
 *
 * <p>Generic types are fully supported: {@code create(new TypeRef<List<Person>>() {})} passes the
 * complete parameterized type to JSON-B, so collections round-trip with their element types intact.
 *
 * <p>JSON-B binds through reflection and requires the bound types to be public with accessible
 * properties (public records and beans); non-public nested types fail with a {@code JsonbException}
 * at first use.
 *
 * <p>Instances are thread-safe if the supplied {@link Jsonb} is; the reference implementation's is.
 */
public class JsonbCodecFactory implements CodecFactory {

  private final Jsonb jsonb;

  /**
   * Creates a factory over the given JSON-B instance.
   *
   * @param jsonb the JSON-B instance every codec will serialize through
   */
  public JsonbCodecFactory(Jsonb jsonb) {
    this.jsonb = Objects.requireNonNull(jsonb, "jsonb must not be null");
  }

  @Override
  public <T> Codec<T> create(TypeRef<T> typeRef) {
    Objects.requireNonNull(typeRef, "typeRef must not be null");
    return new JsonbCodec<>(jsonb, typeRef.getType());
  }
}
