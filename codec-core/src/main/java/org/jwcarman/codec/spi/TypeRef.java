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

  /** Captures the type argument supplied by the anonymous subclass. */
  protected TypeRef() {
    Type superclass = getClass().getGenericSuperclass();
    this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
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
