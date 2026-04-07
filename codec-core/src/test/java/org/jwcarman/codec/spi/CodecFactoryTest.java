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
package org.jwcarman.codec.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodecFactoryTest {

  @Test
  void defaultCreateClassShouldDelegateToCreateTypeRef() {
    CodecFactory factory =
        new CodecFactory() {
          @Override
          public <T> Codec<T> create(TypeRef<T> typeRef) {
            assertThat(typeRef.getType()).isEqualTo(String.class);
            return new Codec<>() {
              @Override
              public byte[] encode(T value) {
                return new byte[0];
              }

              @Override
              public T decode(byte[] bytes) {
                return null;
              }
            };
          }
        };

    Codec<String> codec = factory.create(String.class);
    assertThat(codec).isNotNull();
  }
}
