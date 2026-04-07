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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
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

  // --- of(Class) factory ---

  @Test
  void ofClassShouldCaptureType() {
    TypeRef<String> ref = TypeRef.of(String.class);
    assertThat(ref.getType()).isEqualTo(String.class);
  }

  @Test
  void ofClassShouldRejectNull() {
    assertThatThrownBy(() -> TypeRef.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("type must not be null");
  }

  @Test
  void ofClassShouldWorkWithPrimitiveArrayType() {
    TypeRef<byte[]> ref = TypeRef.of(byte[].class);
    assertThat(ref.getType()).isEqualTo(byte[].class);
  }

  // --- equals / hashCode ---

  @Test
  void twoAnonymousTypeRefsForSameTypeShouldBeEqual() {
    TypeRef<List<String>> ref1 = new TypeRef<>() {};
    TypeRef<List<String>> ref2 = new TypeRef<>() {};
    assertThat(ref1).isEqualTo(ref2);
    assertThat(ref1).hasSameHashCodeAs(ref2);
  }

  @Test
  void typeRefsForDifferentTypeArgsShouldNotBeEqual() {
    TypeRef<List<String>> ref1 = new TypeRef<>() {};
    TypeRef<List<Integer>> ref2 = new TypeRef<>() {};
    assertThat(ref1).isNotEqualTo(ref2);
  }

  @Test
  void ofClassAndAnonymousSubclassForNonGenericTypeShouldBeEqual() {
    TypeRef<String> fromFactory = TypeRef.of(String.class);
    TypeRef<String> fromAnonymous = new TypeRef<>() {};
    assertThat(fromFactory).isEqualTo(fromAnonymous);
    assertThat(fromFactory).hasSameHashCodeAs(fromAnonymous);
  }

  @Test
  void equalsShouldReturnTrueForSameInstance() {
    TypeRef<String> ref = TypeRef.of(String.class);
    assertThat(ref).isEqualTo(ref);
  }

  @Test
  void equalsShouldReturnFalseForNull() {
    TypeRef<String> ref = TypeRef.of(String.class);
    assertThat(ref).isNotEqualTo(null);
  }

  @Test
  void equalsShouldReturnFalseForNonTypeRef() {
    TypeRef<String> ref = TypeRef.of(String.class);
    assertThat(ref).isNotEqualTo("not a TypeRef");
  }

  // --- Map key behavior ---

  @Test
  void shouldWorkAsMapKeyWithAnonymousInstances() {
    Map<TypeRef<?>, String> map = new HashMap<>();
    TypeRef<List<String>> key1 = new TypeRef<>() {};
    map.put(key1, "list-of-string");

    TypeRef<List<String>> key2 = new TypeRef<>() {};
    assertThat(map).containsEntry(key2, "list-of-string");
  }

  @Test
  void shouldWorkAsMapKeyWithOfFactory() {
    Map<TypeRef<?>, String> map = new HashMap<>();
    map.put(TypeRef.of(String.class), "string");

    assertThat(map).containsEntry(TypeRef.of(String.class), "string");
  }

  @Test
  void shouldWorkAsMapKeyMixingOfAndAnonymous() {
    Map<TypeRef<?>, String> map = new HashMap<>();
    map.put(TypeRef.of(String.class), "string");

    TypeRef<String> anonymousRef = new TypeRef<>() {};
    assertThat(map).containsEntry(anonymousRef, "string");
  }

  @Test
  void shouldDistinguishDifferentTypesAsMapKeys() {
    Map<TypeRef<?>, String> map = new HashMap<>();
    map.put(new TypeRef<List<String>>() {}, "list-string");
    map.put(new TypeRef<List<Integer>>() {}, "list-integer");

    assertThat(map).containsEntry(new TypeRef<List<String>>() {}, "list-string");
    assertThat(map).containsEntry(new TypeRef<List<Integer>>() {}, "list-integer");
  }

  // --- toString ---

  @Test
  void toStringShouldIncludeSimpleClassName() {
    TypeRef<String> ref = TypeRef.of(String.class);
    assertThat(ref).hasToString("TypeRef<java.lang.String>");
  }

  @Test
  void toStringShouldIncludeParameterizedTypeName() {
    TypeRef<List<String>> ref = new TypeRef<>() {};
    assertThat(ref).hasToString("TypeRef<java.util.List<java.lang.String>>");
  }

  @Test
  void toStringShouldIncludeNestedParameterizedTypeName() {
    TypeRef<Map<String, List<Integer>>> ref = new TypeRef<>() {};
    assertThat(ref)
        .hasToString("TypeRef<java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>>");
  }
}
