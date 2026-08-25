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
package org.jwcarman.codec.fory;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;
import org.jwcarman.codec.spi.Codec;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.codec.spi.TypeRef;

/**
 * A {@link CodecFactory} backed by <a href="https://fory.apache.org">Apache Fory</a>: fast binary
 * serialization for JVM-to-JVM payloads such as cache entries, queue messages and journal records.
 *
 * <p><strong>Class registration is mandatory.</strong> Fory refuses to serialize or deserialize a
 * class that has not been registered, which is what closes the deserialization-gadget class of
 * attacks. This factory never relaxes that: build the {@link ThreadSafeFory} with {@code
 * requireClassRegistration(true)} (the library default, and what {@link #of(Class[])} does) and
 * register every type a codec will carry, including the element types of collections.
 *
 * <p>The wire format is Fory's own and JVM-specific. It is the right choice when Java is on both
 * ends and speed and size matter; it is the wrong choice for anything another language will read,
 * or for data that must outlive the classes that wrote it — use a schema-based or JSON backend for
 * those.
 *
 * <p>Codecs must be thread-safe, and a plain {@link Fory} is not, so this factory accepts only a
 * {@link ThreadSafeFory} — which is what {@link #of(Class[])} builds, and what {@code
 * Fory.builder()...buildThreadSafeFory()} gives a caller who configures their own.
 *
 * <p>{@link #create(TypeRef)} fails fast: if the instance requires registration and the requested
 * type — or any class named in its type arguments — is not registered, it throws {@link
 * IllegalArgumentException} at creation rather than letting the first {@code encode} fail in
 * production. The check walks declared generics ({@code List<Person>} checks {@code Person}); it
 * cannot see types that only appear at runtime inside registered classes' fields, which Fory itself
 * still rejects.
 */
public class ForyCodecFactory implements CodecFactory {

  private final ThreadSafeFory fory;

  /**
   * Creates a factory over a caller-configured thread-safe Fory instance.
   *
   * @param fory the Fory instance every codec will serialize through; build it with class
   *     registration required unless you have a specific reason not to
   */
  public ForyCodecFactory(ThreadSafeFory fory) {
    this.fory = Objects.requireNonNull(fory, "fory must not be null");
  }

  /**
   * Creates a factory over a new thread-safe Fory instance, in Java mode with class registration
   * required, with the given classes registered. Register every type a codec will carry — records,
   * beans, and the element types of the collections they hold; the JDK's collections and boxed
   * types are registered by Fory itself.
   *
   * @param classes the classes codecs from this factory may serialize
   * @return a factory ready to create codecs for the registered classes
   */
  public static ForyCodecFactory of(Class<?>... classes) {
    ThreadSafeFory fory =
        Fory.builder()
            .withLanguage(Language.JAVA)
            .requireClassRegistration(true)
            .buildThreadSafeFory();
    for (Class<?> type : classes) {
      fory.register(type);
    }
    return new ForyCodecFactory(fory);
  }

  @Override
  public <T> Codec<T> create(TypeRef<T> typeRef) {
    Objects.requireNonNull(typeRef, "typeRef must not be null");
    requireRegistered(typeRef.getType());
    return new ForyCodec<>(fory, rawClass(typeRef.getType()));
  }

  /**
   * Reports whether this factory's Fory instance will serialize values of the given class. True
   * when registration is not required; when the class is registered; or when the class is not
   * something Fory serializes by its own identity — a JDK type, an interface, or an abstract class
   * — because Fory registers the JDK's concrete types itself and decides interfaces and abstract
   * types by the runtime class of each value.
   *
   * @param type the class to check
   * @return whether codecs from this factory can carry values declared as that class
   */
  public boolean supports(Class<?> type) {
    Objects.requireNonNull(type, "type must not be null");
    if (!needsRegistration(type)) {
      return true;
    }
    return fory.execute(
        f -> !f.getConfig().requireClassRegistration() || f.getTypeResolver().isRegistered(type));
  }

  private static boolean needsRegistration(Class<?> type) {
    return !type.isPrimitive()
        && !type.isInterface()
        && !Modifier.isAbstract(type.getModifiers())
        && !type.isArray()
        && !type.getName().startsWith("java.")
        && !type.getName().startsWith("javax.");
  }

  private void requireRegistered(Type type) {
    List<Class<?>> unregistered = new ArrayList<>();
    collectUnregistered(type, unregistered);
    if (!unregistered.isEmpty()) {
      throw new IllegalArgumentException(
          "Not registered with this Fory instance: "
              + unregistered.stream().map(Class::getName).toList()
              + " — register it (ForyCodecFactory.of(...) or fory.register(...)) before creating codecs");
    }
  }

  private void collectUnregistered(Type type, List<Class<?>> unregistered) {
    if (type instanceof Class<?> raw) {
      if (!supports(raw)) {
        unregistered.add(raw);
      }
    } else if (type instanceof ParameterizedType parameterized) {
      collectUnregistered(parameterized.getRawType(), unregistered);
      for (Type argument : parameterized.getActualTypeArguments()) {
        collectUnregistered(argument, unregistered);
      }
    }
  }

  private static Class<?> rawClass(Type type) {
    if (type instanceof Class<?> raw) {
      return raw;
    }
    if (type instanceof ParameterizedType parameterized) {
      return (Class<?>) parameterized.getRawType();
    }
    throw new IllegalArgumentException("Unsupported type: " + type.getTypeName());
  }
}
