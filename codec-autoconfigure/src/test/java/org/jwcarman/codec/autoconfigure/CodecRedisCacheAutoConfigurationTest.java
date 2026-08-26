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
package org.jwcarman.codec.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.ByteBuffer;
import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.CodecFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CodecRedisCacheAutoConfigurationTest {

  public record Person(String name, int age) {}

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonCodecAutoConfiguration.class, CodecRedisCacheAutoConfiguration.class));

  private static RedisCacheManagerBuilder customized(
      RedisCacheManagerBuilderCustomizer customizer) {
    RedisCacheManagerBuilder builder =
        RedisCacheManager.builder()
            .cacheDefaults(
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(7)));
    customizer.customize(builder);
    return builder;
  }

  private static byte[] bytes(ByteBuffer buffer) {
    byte[] out = new byte[buffer.remaining()];
    buffer.get(out);
    return out;
  }

  @Nested
  class Registration {

    @Test
    void registers_a_customizer_when_a_factory_and_redis_cache_support_are_present() {
      contextRunner.run(
          context -> assertThat(context).hasSingleBean(RedisCacheManagerBuilderCustomizer.class));
    }

    @Test
    void backs_off_without_a_codec_factory() {
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CodecRedisCacheAutoConfiguration.class))
          .run(
              context ->
                  assertThat(context).doesNotHaveBean(RedisCacheManagerBuilderCustomizer.class));
    }

    @Test
    void backs_off_when_spring_data_redis_is_absent() {
      contextRunner
          .withClassLoader(new FilteredClassLoader(RedisCacheConfiguration.class))
          .run(
              context ->
                  assertThat(context).doesNotHaveBean(RedisCacheManagerBuilderCustomizer.class));
    }

    @Test
    void backs_off_when_disabled() {
      contextRunner
          .withPropertyValues("codec.redis.cache.enabled=false")
          .run(
              context ->
                  assertThat(context).doesNotHaveBean(RedisCacheManagerBuilderCustomizer.class));
    }
  }

  @Nested
  class Properties {

    @Test
    void bind_from_the_environment() {
      contextRunner
          .withPropertyValues(
              "codec.redis.cache.enabled=true",
              "codec.redis.cache.default-type=java.lang.Object",
              "codec.redis.cache.caches.people=" + Person.class.getName())
          .run(
              context -> {
                CodecRedisCacheProperties properties =
                    context.getBean(CodecRedisCacheProperties.class);

                assertThat(properties.isEnabled()).isTrue();
                assertThat(properties.getDefaultType()).isEqualTo("java.lang.Object");
                assertThat(properties.getCaches()).containsEntry("people", Person.class.getName());
              });
    }
  }

  @Nested
  class Cache_configuration {

    @Test
    void a_listed_cache_serializes_values_through_the_factory_and_keeps_the_other_defaults() {
      contextRunner
          .withPropertyValues("codec.redis.cache.caches.people=" + Person.class.getName())
          .run(
              context -> {
                RedisCacheManagerBuilder builder =
                    customized(context.getBean(RedisCacheManagerBuilderCustomizer.class));
                CodecFactory factory = context.getBean(CodecFactory.class);
                Person alice = new Person("Alice", 30);

                RedisCacheConfiguration people =
                    builder.getCacheConfigurationFor("people").orElseThrow();
                SerializationPair<Object> values = people.getValueSerializationPair();
                assertThat(bytes(values.write(alice)))
                    .isEqualTo(factory.create(Person.class).encode(alice));
                assertThat(values.read(ByteBuffer.wrap(factory.create(Person.class).encode(alice))))
                    .isEqualTo(alice);
                assertThat(people.getTtlFunction().getTimeToLive("k", null))
                    .isEqualTo(Duration.ofMinutes(7));
              });
    }

    @Test
    void unlisted_caches_keep_the_default_serializer_unless_a_default_type_is_set() {
      contextRunner
          .withPropertyValues("codec.redis.cache.caches.people=" + Person.class.getName())
          .run(
              context -> {
                RedisCacheManagerBuilder builder =
                    customized(context.getBean(RedisCacheManagerBuilderCustomizer.class));

                assertThat(builder.getCacheConfigurationFor("orders")).isEmpty();
                assertThat(bytes(builder.cacheDefaults().getValueSerializationPair().write("x")))
                    .isNotEqualTo("\"x\"".getBytes());
              });
    }

    @Test
    void a_default_type_serializes_every_cache_through_the_factory() {
      contextRunner
          .withPropertyValues("codec.redis.cache.default-type=" + Person.class.getName())
          .run(
              context -> {
                RedisCacheManagerBuilder builder =
                    customized(context.getBean(RedisCacheManagerBuilderCustomizer.class));
                CodecFactory factory = context.getBean(CodecFactory.class);
                Person alice = new Person("Alice", 30);

                assertThat(bytes(builder.cacheDefaults().getValueSerializationPair().write(alice)))
                    .isEqualTo(factory.create(Person.class).encode(alice));
                assertThat(builder.cacheDefaults().getTtlFunction().getTimeToLive("k", null))
                    .isEqualTo(Duration.ofMinutes(7));
              });
    }

    @Test
    void an_unknown_value_type_fails_with_the_property_named() {
      contextRunner
          .withPropertyValues("codec.redis.cache.caches.people=com.example.Missing")
          .run(
              context -> {
                RedisCacheManagerBuilderCustomizer customizer =
                    context.getBean(RedisCacheManagerBuilderCustomizer.class);

                assertThatIllegalStateException()
                    .isThrownBy(() -> customized(customizer))
                    .withMessageContaining("codec.redis.cache")
                    .withMessageContaining("com.example.Missing");
              });
    }
  }
}
