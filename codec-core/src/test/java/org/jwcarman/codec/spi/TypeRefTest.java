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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
  void shouldRejectATypeVariable() {
    assertThatThrownBy(TypeRefTest::<String>captureUnresolved)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("type variable T");
  }

  /** A generic method: {@code T} is erased here, so the anonymous subclass captures a variable. */
  private static <T> TypeRef<T> captureUnresolved() {
    return new TypeRef<T>() {};
  }

  @Test
  void rawClassOfAPlainClassIsTheClass() {
    assertThat(TypeRef.of(String.class).rawClass()).isEqualTo(String.class);
    assertThat(new TypeRef<Integer>() {}.rawClass()).isEqualTo(Integer.class);
  }

  @Test
  void rawClassOfAParameterizedTypeIsItsRawType() {
    assertThat(new TypeRef<List<String>>() {}.rawClass()).isEqualTo(List.class);
    assertThat(new TypeRef<Map<String, List<Integer>>>() {}.rawClass()).isEqualTo(Map.class);
  }

  @Test
  void rawClassOfAPrimitiveArrayIsTheArrayClass() {
    assertThat(TypeRef.of(int[].class).rawClass()).isEqualTo(int[].class);
  }

  @Test
  void rawClassOfAGenericArrayTypeIsRejected() {
    TypeRef<List<String>[]> genericArray = new TypeRef<>() {};

    assertThatThrownBy(genericArray::rawClass)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Unsupported type: ")
        .hasMessageContaining("List<java.lang.String>[]");
  }

  @Test
  void rawClassIsUsableAsACheckedCast() {
    // The whole point: a Class<T> lets a backend use Class.cast instead of an unchecked (T) cast.
    Class<List<String>> raw = new TypeRef<List<String>>() {}.rawClass();
    Object value = List.of("a");

    assertThat(raw.cast(value)).containsExactly("a");
    assertThatThrownBy(() -> raw.cast("not a list")).isInstanceOf(ClassCastException.class);
  }

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
    assertThat(ref1).isEqualTo(ref2).hasSameHashCodeAs(ref2);
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
    assertThat(fromFactory).isEqualTo(fromAnonymous).hasSameHashCodeAs(fromAnonymous);
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

    assertThat(map)
        .containsEntry(new TypeRef<List<String>>() {}, "list-string")
        .containsEntry(new TypeRef<List<Integer>>() {}, "list-integer");
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

  @Nested
  @DisplayName("Combinators build parameterized types at runtime")
  class Combinators {

    record Person(String name) {}

    @Test
    void listOfEqualsTheCapturedType() {
      TypeRef<List<Person>> built = TypeRef.listOf(TypeRef.of(Person.class));
      TypeRef<List<Person>> captured = new TypeRef<>() {};

      assertThat(built).isEqualTo(captured);
      assertThat(captured).isEqualTo(built);
      assertThat(built.hashCode()).isEqualTo(captured.hashCode());
      assertThat(built.getType()).isEqualTo(captured.getType());
      assertThat(captured.getType()).isEqualTo(built.getType());
      assertThat(built).hasToString(captured.toString());
    }

    @Test
    void setOfAndOptionalOfEqualTheirCapturedTypes() {
      assertThat(TypeRef.setOf(TypeRef.of(Person.class))).isEqualTo(new TypeRef<Set<Person>>() {});
      assertThat(TypeRef.optionalOf(TypeRef.of(Person.class)))
          .isEqualTo(new TypeRef<Optional<Person>>() {});
    }

    @Test
    void mapOfEqualsTheCapturedType() {
      TypeRef<Map<String, Person>> built =
          TypeRef.mapOf(TypeRef.of(String.class), TypeRef.of(Person.class));

      assertThat(built).isEqualTo(new TypeRef<Map<String, Person>>() {});
      assertThat(built)
          .hasToString(
              "TypeRef<java.util.Map<java.lang.String, " + Person.class.getTypeName() + ">>");
    }

    @Test
    void combinatorsNest() {
      TypeRef<Map<String, List<Person>>> built =
          TypeRef.mapOf(TypeRef.of(String.class), TypeRef.listOf(TypeRef.of(Person.class)));

      assertThat(built).isEqualTo(new TypeRef<Map<String, List<Person>>>() {});
      assertThat(built.rawClass()).isEqualTo(Map.class);
    }

    @Test
    void builtTypesAreInterchangeableAsMapKeys() {
      Map<TypeRef<?>, String> cache = new HashMap<>();
      cache.put(new TypeRef<List<Person>>() {}, "captured");

      assertThat(cache.get(TypeRef.listOf(TypeRef.of(Person.class)))).isEqualTo("captured");
      assertThat(cache.get(TypeRef.listOf(TypeRef.of(String.class)))).isNull();
    }

    record Envelope<O>(String id, O payload) {}

    @Test
    void parameterizedBuildsAUserDefinedGenericType() {
      TypeRef<Person> element = TypeRef.of(Person.class);

      TypeRef<Envelope<Person>> built = TypeRef.parameterized(Envelope.class, element);

      assertThat(built).isEqualTo(new TypeRef<Envelope<Person>>() {});
      assertThat(built.rawClass()).isEqualTo(Envelope.class);
      assertThat(built).hasToString(new TypeRef<Envelope<Person>>() {}.toString());
    }

    @Test
    void parameterizedBuildsAJdkGenericType() {
      TypeRef<Map<String, Integer>> built =
          TypeRef.parameterized(Map.class, TypeRef.of(String.class), TypeRef.of(Integer.class));

      assertThat(built).isEqualTo(new TypeRef<Map<String, Integer>>() {});
    }

    @Test
    void rejectsAPrimitiveTypeArgument() {
      TypeRef<Integer> primitive = TypeRef.of(int.class);

      assertThatThrownBy(() -> TypeRef.listOf(primitive))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("primitive")
          .hasMessageContaining("int");
    }

    @Test
    void parameterizedRejectsTheWrongNumberOfArguments() {
      TypeRef<String> string = TypeRef.of(String.class);

      assertThatThrownBy(() -> TypeRef.parameterized(List.class, string, string))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("List")
          .hasMessageContaining("1")
          .hasMessageContaining("2");
      assertThatThrownBy(() -> TypeRef.parameterized(String.class, string))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("String");
    }

    @Test
    void parameterizedRejectsNulls() {
      TypeRef<String> string = TypeRef.of(String.class);

      assertThatThrownBy(() -> TypeRef.parameterized(null, string))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> TypeRef.listOf(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> TypeRef.parameterized(List.class, (TypeRef<?>) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aNestedGenericClassNamesItsOwner() {
      TypeRef<Map.Entry<String, Integer>> built =
          TypeRef.parameterized(
              Map.Entry.class, TypeRef.of(String.class), TypeRef.of(Integer.class));
      TypeRef<Map.Entry<String, Integer>> captured = new TypeRef<>() {};

      assertThat(built).isEqualTo(captured);
      assertThat(built).hasToString(captured.toString());
      assertThat(built)
          .hasToString("TypeRef<java.util.Map$Entry<java.lang.String, java.lang.Integer>>");
    }

    @Test
    void builtTypesFollowTheJdkEqualityContract() {
      Type list = TypeRef.listOf(TypeRef.of(Person.class)).getType();
      Type entry =
          TypeRef.<Map.Entry<String, Integer>>parameterized(
                  Map.Entry.class, TypeRef.of(String.class), TypeRef.of(Integer.class))
              .getType();
      ParameterizedType entryWithoutOwner =
          new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
              return new Type[] {String.class, Integer.class};
            }

            @Override
            public Type getRawType() {
              return Map.Entry.class;
            }

            @Override
            public Type getOwnerType() {
              return null;
            }
          };

      assertThat(list).isEqualTo(list);
      assertThat(list).isNotEqualTo(List.class);
      assertThat(list).isNotEqualTo(TypeRef.setOf(TypeRef.of(Person.class)).getType());
      assertThat(list).isNotEqualTo(TypeRef.listOf(TypeRef.of(String.class)).getType());
      assertThat(entry).isNotEqualTo(entryWithoutOwner);
      assertThat(list.hashCode()).isEqualTo(new TypeRef<List<Person>>() {}.getType().hashCode());
      assertThat(((ParameterizedType) list).getActualTypeArguments()).containsExactly(Person.class);
      assertThat(list).hasToString(list.getTypeName());
    }
  }
}
