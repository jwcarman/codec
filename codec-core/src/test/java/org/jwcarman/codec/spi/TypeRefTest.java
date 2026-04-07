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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypeRefTest {

  @Test
  void shouldCaptureSimpleGenericType() {
    TypeRef<List<String>> ref = new TypeRef<>() {};
    Type type = ref.getType();

    assertThat(type).isInstanceOf(ParameterizedType.class);
    ParameterizedType paramType = (ParameterizedType) type;
    assertThat(paramType.getRawType()).isEqualTo(List.class);
    assertThat(paramType.getActualTypeArguments()).containsExactly(String.class);
  }

  @Test
  void shouldCaptureMapGenericType() {
    TypeRef<Map<String, Integer>> ref = new TypeRef<>() {};
    Type type = ref.getType();

    assertThat(type).isInstanceOf(ParameterizedType.class);
    ParameterizedType paramType = (ParameterizedType) type;
    assertThat(paramType.getRawType()).isEqualTo(Map.class);
    assertThat(paramType.getActualTypeArguments()).containsExactly(String.class, Integer.class);
  }

  @Test
  void shouldCaptureNestedGenericType() {
    TypeRef<List<Map<String, List<Integer>>>> ref = new TypeRef<>() {};
    Type type = ref.getType();

    assertThat(type).isInstanceOf(ParameterizedType.class);
    ParameterizedType paramType = (ParameterizedType) type;
    assertThat(paramType.getRawType()).isEqualTo(List.class);

    Type innerType = paramType.getActualTypeArguments()[0];
    assertThat(innerType).isInstanceOf(ParameterizedType.class);
    ParameterizedType innerParamType = (ParameterizedType) innerType;
    assertThat(innerParamType.getRawType()).isEqualTo(Map.class);
  }
}
