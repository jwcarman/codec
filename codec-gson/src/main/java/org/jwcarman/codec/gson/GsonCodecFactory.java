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
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.codec.spi.TypeRef;

public class GsonCodecFactory implements CodecFactory {

  private final Gson gson;

  public GsonCodecFactory(Gson gson) {
    this.gson = gson;
  }

  @Override
  public <T> Codec<T> create(TypeRef<T> typeRef) {
    TypeToken<T> typeToken = (TypeToken<T>) TypeToken.get(typeRef.getType());
    return new GsonCodec<>(gson, typeToken);
  }
}
