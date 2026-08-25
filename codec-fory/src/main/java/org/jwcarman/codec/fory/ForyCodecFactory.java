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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import org.apache.fory.BaseFory;
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
 * attacks. This factory never relaxes that: build the {@link BaseFory} with {@code
 * requireClassRegistration(true)} (the library default, and what {@link #of(Class[])} does) and
 * register every type a codec will carry, including the element types of collections.
 *
 * <p>The wire format is Fory's own and JVM-specific. It is the right choice when Java is on both
 * ends and speed and size matter; it is the wrong choice for anything another language will read,
 * or for data that must outlive the classes that wrote it — use a schema-based or JSON backend for
 * those.
 *
 * <p>A plain {@link Fory} instance is not thread-safe. Pass a {@link ThreadSafeFory} (as {@link
 * #of(Class[])} builds) unless the codec will only ever be used from one thread.
 */
public class ForyCodecFactory implements CodecFactory {

  private final BaseFory fory;

  /**
   * Creates a factory over a caller-configured Fory instance.
   *
   * @param fory the Fory instance every codec will serialize through; must require class
   *     registration and should be a {@link ThreadSafeFory} for shared use
   */
  public ForyCodecFactory(BaseFory fory) {
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
    return new ForyCodec<>(fory, rawClass(typeRef.getType()));
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
