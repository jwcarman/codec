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
package org.jwcarman.codec;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
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
 * are equal. The one exception is {@link #parameterized} applied to an inner class of a
 * <em>generic</em> outer class, which cannot carry the outer's type arguments — see that method.
 *
 * @param <T> the captured type
 */
public abstract class TypeRef<T> {
  private final Type type;

  /**
   * Captures the type argument supplied by the subclass.
   *
   * <p>The subclass need not extend {@code TypeRef} directly. {@code T} is resolved through the
   * whole hierarchy, so an abstraction of your own — {@code abstract class EnvelopeCodec<E> extends
   * TypeRef<Envelope<E>>} — captures what it declares, and a subclass that rebinds a parameter on
   * the way up is followed to the end of the chain.
   *
   * @throws IllegalArgumentException if the captured argument is a type variable — {@code new
   *     TypeRef<T>() {}} inside a generic method captures nothing a backend can use, and would
   *     otherwise be silently mapped to {@code Object}. Extending {@code TypeRef} raw binds nothing
   *     to {@code T} and is rejected the same way
   */
  protected TypeRef() {
    Map<TypeVariable<?>, Type> bindings = bindings(getClass());
    TypeVariable<?> t = TypeRef.class.getTypeParameters()[0];
    // Extending TypeRef raw binds nothing to T, which leaves T standing for itself — the same
    // shape as capturing an erased variable, and rejected by the same guard.
    Type captured = substitute(bindings.getOrDefault(t, t), bindings);
    if (captured instanceof TypeVariable<?>) {
      throw new IllegalArgumentException(
          "TypeRef cannot capture the type variable "
              + captured
              + ": the type argument must be concrete where the anonymous subclass is created");
    }
    this.type = captured;
  }

  /**
   * Collects what each type variable is bound to, from {@code subclass} up to {@code TypeRef}.
   *
   * <p>A level binds its superclass's parameters to the arguments it passes: {@code class Mid<A, B>
   * extends TypeRef<B>} binds {@code TypeRef.T} to {@code Mid.B}, and {@code new Mid<Integer,
   * String>() {}} binds {@code Mid.B} to {@code String}. Reading only the immediate superclass's
   * first argument — which is what this used to do — sees {@code Integer}.
   */
  private static Map<TypeVariable<?>, Type> bindings(Class<?> subclass) {
    Map<TypeVariable<?>, Type> bindings = new HashMap<>();
    // getClass() extends TypeRef, so the walk always terminates there.
    for (Class<?> current = subclass; current != TypeRef.class; current = current.getSuperclass()) {
      // A level that names its superclass raw, or without arguments, contributes nothing.
      if (current.getGenericSuperclass() instanceof ParameterizedType parameterized) {
        TypeVariable<?>[] parameters = ((Class<?>) parameterized.getRawType()).getTypeParameters();
        Type[] arguments = parameterized.getActualTypeArguments();
        for (int i = 0; i < parameters.length; i++) {
          bindings.put(parameters[i], arguments[i]);
        }
      }
    }
    return bindings;
  }

  /**
   * Replaces every bound type variable in {@code type}, throughout its structure.
   *
   * <p>A partial substitution is worse than none: it yields a type that claims to be concrete while
   * a variable is still sitting in it, and the failure surfaces far from here. So every shape that
   * can contain a variable is descended into and rebuilt, and a variable with no binding is left
   * exactly as it was rather than dropped.
   */
  private static Type substitute(Type type, Map<TypeVariable<?>, Type> bindings) {
    return switch (type) {
      case TypeVariable<?> variable -> substituteVariable(variable, bindings);
      case ParameterizedType parameterized -> substituteParameterized(parameterized, bindings);
      case GenericArrayType array -> substituteArray(array, bindings);
      case WildcardType wildcard -> substituteWildcard(wildcard, bindings);
      case null, default -> type;
    };
  }

  private static Type substituteVariable(
      TypeVariable<?> variable, Map<TypeVariable<?>, Type> bindings) {
    Type bound = bindings.get(variable);
    // An unbound variable stays as it is: the constructor rejects it by name.
    if (bound == null) {
      return variable;
    }
    // The binding may name another bound variable: class Nested<X> extends Mid<X, List<X>>.
    // No cycle guard is needed. Each binding maps a variable declared in one class to a type
    // written in terms of its subclass's variables, so following one always moves down a
    // hierarchy the JLS forbids to be circular, and the hierarchy is finite.
    return substitute(bound, bindings);
  }

  private static Type substituteParameterized(
      ParameterizedType parameterized, Map<TypeVariable<?>, Type> bindings) {
    Type[] arguments = parameterized.getActualTypeArguments();
    Type[] substituted = new Type[arguments.length];
    boolean changed = false;
    for (int i = 0; i < arguments.length; i++) {
      substituted[i] = substitute(arguments[i], bindings);
      changed |= substituted[i] != arguments[i];
    }
    Type owner = substitute(parameterized.getOwnerType(), bindings);
    // Nothing moved: keep the type the JDK reflected rather than a copy of it.
    if (!changed && owner == parameterized.getOwnerType()) {
      return parameterized;
    }
    return new Parameterized(owner, (Class<?>) parameterized.getRawType(), substituted);
  }

  private static Type substituteArray(GenericArrayType array, Map<TypeVariable<?>, Type> bindings) {
    Type component = substitute(array.getGenericComponentType(), bindings);
    if (component == array.getGenericComponentType()) {
      return array;
    }
    // The JDK models an array of a non-generic type as a Class, so match that or a substituted
    // array would not equal the same array captured directly.
    return component instanceof Class<?> clazz ? clazz.arrayType() : new GenericArray(component);
  }

  private static Type substituteWildcard(
      WildcardType wildcard, Map<TypeVariable<?>, Type> bindings) {
    Type[] upper = substituteBounds(wildcard.getUpperBounds(), bindings);
    Type[] lower = substituteBounds(wildcard.getLowerBounds(), bindings);
    if (Arrays.equals(upper, wildcard.getUpperBounds())
        && Arrays.equals(lower, wildcard.getLowerBounds())) {
      return wildcard;
    }
    return new Wildcard(upper, lower);
  }

  private static Type[] substituteBounds(Type[] bounds, Map<TypeVariable<?>, Type> bindings) {
    Type[] substituted = new Type[bounds.length];
    for (int i = 0; i < bounds.length; i++) {
      substituted[i] = substitute(bounds[i], bindings);
    }
    return substituted;
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
   * TypeRef<LinkedList<O>>} from {@code List.class}).
   *
   * <p>One shape cannot be built here at all: an inner class of a <em>generic</em> outer class. A
   * class literal has already discarded the outer's arguments, so {@code
   * parameterized(Outer.Inner.class, of(Integer.class))} produces {@code Outer.Inner<Integer>},
   * which is not equal to the {@code Outer<String>.Inner<Integer>} an anonymous subclass captures
   * and will not find it as a cache key. Capture that shape with an anonymous subclass instead.
   * Such a mismatch does not fail in the codec: decode succeeds with a value of the built type, and
   * the caller sees a {@code ClassCastException} where the decoded value is first used. Round-trip
   * a reference built here once in a test.
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
   * <p>This method contains the one unchecked cast in the codebase. It is sound for every reference
   * whose {@code T} the compiler established — one captured by a subclass, or built by {@link
   * #listOf} and the other typed combinators — because such a reference captures {@code T} and
   * nothing else, so the erasure of the captured type is the erasure of {@code T}. It is
   * <em>not</em> sound for a reference from {@link #parameterized}, whose {@code T} is asserted by
   * the caller rather than proven: {@code parameterized(List.class, of(String.class))} assigned to
   * a {@code TypeRef<ArrayList<String>>} returns {@code List.class} here, and the caller sees a
   * {@code ClassCastException} where the value is first used. Backends use the result with {@link
   * Class#cast} — a checked cast — instead of an unchecked {@code (T)} cast of their own; every
   * other cast in the reactor is a checked {@code Class.cast} or none at all, and no new unchecked
   * cast is permitted anywhere else. The build compiles with {@code
   * -Xlint:all,-processing,-unchecked -Werror}: the {@code unchecked} category is off precisely and
   * only because of this method, since Java cannot express the type-token bridge without it and
   * cannot write an unchecked cast without a warning.
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
    private final Type ownerType;
    private final Class<?> raw;
    private final Type[] arguments;

    Parameterized(Class<?> raw, Type[] arguments) {
      this(raw.getDeclaringClass(), raw, arguments);
    }

    /**
     * @param ownerType the enclosing type, which substitution may have rewritten — {@code
     *     Outer<String>} rather than the raw {@code Outer} the declaring class would give
     */
    Parameterized(Type ownerType, Class<?> raw, Type[] arguments) {
      this.ownerType = ownerType;
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
      return ownerType;
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

  /**
   * A {@link GenericArrayType} built by substitution. Equality and hashing follow the JDK's own
   * implementation, so a substituted array and the same array captured directly are
   * interchangeable.
   */
  private static final class GenericArray implements GenericArrayType {
    private final Type componentType;

    GenericArray(Type componentType) {
      this.componentType = componentType;
    }

    @Override
    public Type getGenericComponentType() {
      return componentType;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof GenericArrayType other
          && componentType.equals(other.getGenericComponentType());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(componentType);
    }

    @Override
    public String getTypeName() {
      return componentType.getTypeName() + "[]";
    }

    @Override
    public String toString() {
      return getTypeName();
    }
  }

  /**
   * A {@link WildcardType} built by substitution. Equality and hashing follow the JDK's own
   * implementation, which reads the bounds arrays directly.
   */
  private static final class Wildcard implements WildcardType {
    private final Type[] upperBounds;
    private final Type[] lowerBounds;

    Wildcard(Type[] upperBounds, Type[] lowerBounds) {
      this.upperBounds = upperBounds;
      this.lowerBounds = lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds.clone();
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds.clone();
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof WildcardType other
          && Arrays.equals(upperBounds, other.getUpperBounds())
          && Arrays.equals(lowerBounds, other.getLowerBounds());
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(upperBounds) ^ Arrays.hashCode(lowerBounds);
    }

    @Override
    public String getTypeName() {
      if (lowerBounds.length > 0) {
        return "? super " + lowerBounds[0].getTypeName();
      }
      // WildcardType reports at least Object as an upper bound, so there is always one to read.
      if (Object.class.equals(upperBounds[0])) {
        return "?";
      }
      return "? extends " + upperBounds[0].getTypeName();
    }

    @Override
    public String toString() {
      return getTypeName();
    }
  }
}
