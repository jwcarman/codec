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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Super type token capturing a full generic type at compile time, surviving erasure.
 *
 * <p>Instantiate as an anonymous subclass to capture a parameterized type:
 *
 * {@snippet lang = java :
 * TypeRef<List<Person>> ref = new TypeRef<>() {};
 * }
 *
 * <p>A parameterized type can also be built at run time from the type references of its arguments,
 * which keeps the compiler in the loop even when the argument is only known to the caller:
 *
 * {@snippet lang = java :
 * TypeRef<List<O>> batch = TypeRef.listOf(elementType);   // TypeRef<O> supplied at run time
 * }
 *
 * <p>Equality and hashing are based on the captured {@link Type}, so instances are safe to use as
 * cache keys; a type built with {@link #listOf} and the same type captured by an anonymous subclass
 * are equal.
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
   * A reference to {@code List<E>} built from the reference to {@code E}.
   *
   * @param element the element type
   * @param <E> the element type
   * @return a reference to {@code List<E>}, equal to the same type captured by an anonymous
   *     subclass
   * @throws NullPointerException if {@code element} is null
   * @throws IllegalArgumentException if {@code element} is a primitive type
   */
  public static <E> TypeRef<List<E>> listOf(TypeRef<E> element) {
    return new TypeRef<List<E>>(parameterizedType(List.class, element)) {};
  }

  /**
   * A reference to {@code Set<E>} built from the reference to {@code E}.
   *
   * @param element the element type
   * @param <E> the element type
   * @return a reference to {@code Set<E>}
   * @throws NullPointerException if {@code element} is null
   * @throws IllegalArgumentException if {@code element} is a primitive type
   */
  public static <E> TypeRef<Set<E>> setOf(TypeRef<E> element) {
    return new TypeRef<Set<E>>(parameterizedType(Set.class, element)) {};
  }

  /**
   * A reference to {@code Optional<E>} built from the reference to {@code E}.
   *
   * @param element the element type
   * @param <E> the element type
   * @return a reference to {@code Optional<E>}
   * @throws NullPointerException if {@code element} is null
   * @throws IllegalArgumentException if {@code element} is a primitive type
   */
  public static <E> TypeRef<Optional<E>> optionalOf(TypeRef<E> element) {
    return new TypeRef<Optional<E>>(parameterizedType(Optional.class, element)) {};
  }

  /**
   * A reference to {@code Map<K, V>} built from the references to {@code K} and {@code V}.
   *
   * @param key the key type
   * @param value the value type
   * @param <K> the key type
   * @param <V> the value type
   * @return a reference to {@code Map<K, V>}
   * @throws NullPointerException if {@code key} or {@code value} is null
   * @throws IllegalArgumentException if {@code key} or {@code value} is a primitive type
   */
  public static <K, V> TypeRef<Map<K, V>> mapOf(TypeRef<K> key, TypeRef<V> value) {
    return new TypeRef<Map<K, V>>(parameterizedType(Map.class, key, value)) {};
  }

  /**
   * A reference to any generic class applied to the given type arguments — your own {@code
   * Envelope<O>} built from a {@code TypeRef<O>}, for example. The typed combinators ({@link
   * #listOf}, {@link #mapOf} and friends) cover the JDK collections; this covers every other
   * generic class. {@code T} is taken from the assignment context, so declare the reference you
   * mean:
   *
   * {@snippet lang = java :
   * TypeRef<Envelope<O>> ref = TypeRef.parameterized(Envelope.class, elementType);
   * }
   *
   * <p>The class literal is a witness for {@code T}: because a raw type is a supertype of each of
   * its parameterizations, {@code Class<? super T>} lets the compiler reject {@code
   * TypeRef<List<O>> ref = parameterized(Set.class, o)} and {@code TypeRef<Object>} alike. The
   * arity of {@code arguments} is checked at construction, and a class with no type parameters is
   * rejected. What remains unchecked is the identity and order of {@code arguments} against {@code
   * T}'s own type arguments, and {@code T} naming a subtype of {@code raw} ({@code
   * TypeRef<LinkedList<O>>} from {@code List.class}). Such a mismatch does not fail in the codec:
   * decode succeeds with a value of the built type, and the caller sees a {@code
   * ClassCastException} where the decoded value is first used. Round-trip a reference built here
   * once in a test.
   *
   * @param raw the generic class, such as {@code Envelope.class}
   * @param arguments one type reference per type parameter of {@code raw}, in declaration order;
   *     each must be a reference type, not a primitive
   * @param <T> {@code raw} applied to {@code arguments}
   * @return a reference to {@code raw} applied to {@code arguments}
   * @throws NullPointerException if {@code raw}, {@code arguments} or any argument is null
   * @throws IllegalArgumentException if {@code raw} declares no type parameters, if the number of
   *     arguments does not match the number it declares, or if an argument is a primitive type
   */
  public static <T> TypeRef<T> parameterized(Class<? super T> raw, TypeRef<?>... arguments) {
    return new TypeRef<T>(parameterizedType(raw, arguments)) {};
  }

  private static ParameterizedType parameterizedType(Class<?> raw, TypeRef<?>... arguments) {
    Objects.requireNonNull(raw, "raw must not be null");
    Objects.requireNonNull(arguments, "arguments must not be null");
    int expected = raw.getTypeParameters().length;
    if (expected == 0) {
      throw new IllegalArgumentException(raw.getName() + " is not a generic class");
    }
    if (arguments.length != expected) {
      throw new IllegalArgumentException(
          raw.getSimpleName()
              + " declares "
              + expected
              + " type parameter(s) but "
              + arguments.length
              + " argument(s) were given");
    }
    Type[] types = new Type[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      Type argument = Objects.requireNonNull(arguments[i], "arguments must not contain null").type;
      if (argument instanceof Class<?> clazz && clazz.isPrimitive()) {
        throw new IllegalArgumentException(
            "a type argument cannot be primitive: " + clazz.getName() + " (use its wrapper)");
      }
      types[i] = argument;
    }
    return new Parameterized(raw, types);
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
   * @throws IllegalArgumentException if the captured type has no single erased class (a wildcard or
   *     a generic array type)
   */
  public Class<T> rawClass() {
    Class<?> raw =
        switch (type) {
          case Class<?> clazz -> clazz;
          case ParameterizedType parameterized -> (Class<?>) parameterized.getRawType();
          default -> throw new IllegalArgumentException("Unsupported type: " + type.getTypeName());
        };
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

  /**
   * A {@link ParameterizedType} built at run time. Equality, hashing and the type name follow the
   * JDK's own implementation so that a built type and a captured one are the same cache key.
   */
  private static final class Parameterized implements ParameterizedType {
    private final Class<?> raw;
    private final Type[] arguments;

    Parameterized(Class<?> raw, Type[] arguments) {
      this.raw = raw;
      this.arguments = arguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return arguments.clone();
    }

    @Override
    public Type getRawType() {
      return raw;
    }

    @Override
    public Type getOwnerType() {
      return raw.getDeclaringClass();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ParameterizedType other)) return false;
      return Objects.equals(getOwnerType(), other.getOwnerType())
          && raw.equals(other.getRawType())
          && Arrays.equals(arguments, other.getActualTypeArguments());
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(arguments) ^ Objects.hashCode(getOwnerType()) ^ raw.hashCode();
    }

    @Override
    public String getTypeName() {
      StringJoiner args = new StringJoiner(", ", "<", ">");
      for (Type argument : arguments) {
        args.add(argument.getTypeName());
      }
      Type owner = getOwnerType();
      String name = owner == null ? raw.getName() : owner.getTypeName() + "$" + raw.getSimpleName();
      return name + args;
    }

    @Override
    public String toString() {
      return getTypeName();
    }
  }
}
