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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Objects;

/**
 * Super type token capturing a full generic type at compile time, surviving erasure.
 *
 * <p>Instantiate as an anonymous subclass to capture a parameterized type:
 *
 * {@snippet lang = java :
 * TypeRef<List<Person>> ref = new TypeRef<>() {};
 * }
 *
 * <p>Equality and hashing are based on the captured {@link Type}, so instances are safe to use as
 * cache keys.
 *
 * @param <T> the captured type
 */
public abstract class TypeRef<T> {
  private final Type type;

  /**
   * Captures the type argument supplied by the anonymous subclass.
   *
   * @throws IllegalArgumentException if the type argument is a type variable — {@code new
   *     TypeRef<T>() {}} inside a generic method captures nothing a backend can use, and would
   *     otherwise be silently mapped to {@code Object}
   */
  protected TypeRef() {
    Type superclass = getClass().getGenericSuperclass();
    Type captured = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    if (captured instanceof TypeVariable<?>) {
      throw new IllegalArgumentException(
          "TypeRef cannot capture the type variable "
              + captured
              + ": the type argument must be concrete where the anonymous subclass is created");
    }
    this.type = captured;
  }

  private TypeRef(Type type) {
    this.type = type;
  }

  /**
   * Creates a type reference for a non-generic class.
   *
   * @param type the class to reference
   * @param <T> the referenced type
   * @return a type reference for the class
   * @throws NullPointerException if {@code type} is null
   */
  public static <T> TypeRef<T> of(Class<T> type) {
    Objects.requireNonNull(type, "type must not be null");
    return new TypeRef<>(type) {};
  }

  /**
   * Returns the captured type.
   *
   * @return the captured type
   */
  public Type getType() {
    return type;
  }

  /**
   * Returns the erased class of the captured type: {@code List.class} for {@code List<String>}, the
   * class itself for a non-generic type.
   *
   * <p>This method contains the one unchecked cast in the codebase. It is sound by construction: a
   * {@code TypeRef<T>} captures {@code T} and nothing else, so the erasure of the captured type is
   * the erasure of {@code T}. Backends use the result with {@link Class#cast} — a checked cast —
   * instead of an unchecked {@code (T)} cast of their own; every other cast in the reactor is a
   * checked {@code Class.cast} or none at all, and no new unchecked cast is permitted anywhere
   * else. The build compiles with {@code -Xlint:all,-processing,-unchecked -Werror}: the {@code
   * unchecked} category is off precisely and only because of this method, since Java cannot express
   * the type-token bridge without it and cannot write an unchecked cast without a warning.
   *
   * @return the erased class of {@code T}
   * @throws IllegalArgumentException if the captured type is a generic array type, which has no
   *     single erased class a codec could be created for
   */
  public Class<T> rawClass() {
    Class<?> raw;
    if (type instanceof Class<?> clazz) {
      raw = clazz;
    } else if (type instanceof ParameterizedType parameterized) {
      raw = (Class<?>) parameterized.getRawType();
    } else {
      throw new IllegalArgumentException("Unsupported type: " + type.getTypeName());
    }
    return uncheckedTypeToken(raw);
  }

  /**
   * The type-token bridge: the one place the erased class is asserted to be {@code Class<T>}.
   * Isolated so the unchecked cast has exactly one line to live on.
   */
  private static <T> Class<T> uncheckedTypeToken(Class<?> raw) {
    return (Class<T>) raw;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeRef<?> other)) return false;
    return type.equals(other.type);
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public String toString() {
    return "TypeRef<" + type.getTypeName() + ">";
  }
}
