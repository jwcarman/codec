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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.fory.ForyCodecFactory;
import org.jwcarman.codec.gson.GsonCodecFactory;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.jackson2.Jackson2CodecFactory;
import org.jwcarman.codec.jsonb.JsonbCodecFactory;
import org.jwcarman.codec.protobuf.ProtobufCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.codec.spi.TypeRef;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CodecAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ForyCodecAutoConfiguration.class,
                  JacksonCodecAutoConfiguration.class,
                  Jackson2CodecAutoConfiguration.class,
                  GsonCodecAutoConfiguration.class,
                  JsonbCodecAutoConfiguration.class,
                  ProtobufCodecAutoConfiguration.class));

  @Nested
  class Backend_precedence {

    @Test
    void jackson_wins_when_all_backends_are_present() {
      contextRunner.run(
          context -> {
            assertThat(context).hasSingleBean(CodecFactory.class);
            assertThat(context).hasSingleBean(JacksonCodecFactory.class);
          });
    }

    @Test
    void jackson2_wins_when_jackson_is_absent() {
      contextRunner
          .withClassLoader(new FilteredClassLoader(ObjectMapper.class))
          .run(
              context -> {
                assertThat(context).hasSingleBean(CodecFactory.class);
                assertThat(context).hasSingleBean(Jackson2CodecFactory.class);
              });
    }

    @Test
    void gson_wins_when_both_jacksons_are_absent() {
      contextRunner
          .withClassLoader(
              new FilteredClassLoader(
                  ObjectMapper.class, com.fasterxml.jackson.databind.ObjectMapper.class))
          .run(
              context -> {
                assertThat(context).hasSingleBean(CodecFactory.class);
                assertThat(context).hasSingleBean(GsonCodecFactory.class);
              });
    }

    @Test
    void jsonb_wins_when_both_jacksons_and_gson_are_absent() {
      contextRunner
          .withClassLoader(
              new FilteredClassLoader(
                  ObjectMapper.class,
                  com.fasterxml.jackson.databind.ObjectMapper.class,
                  com.google.gson.Gson.class))
          .run(
              context -> {
                assertThat(context).hasSingleBean(CodecFactory.class);
                assertThat(context).hasSingleBean(JsonbCodecFactory.class);
              });
    }

    @Test
    void protobuf_wins_when_only_protobuf_is_present() {
      contextRunner
          .withClassLoader(
              new FilteredClassLoader(
                  ObjectMapper.class,
                  com.fasterxml.jackson.databind.ObjectMapper.class,
                  com.google.gson.Gson.class,
                  jakarta.json.bind.Jsonb.class))
          .run(
              context -> {
                assertThat(context).hasSingleBean(CodecFactory.class);
                assertThat(context).hasSingleBean(ProtobufCodecFactory.class);
              });
    }

    @Test
    void no_factory_is_registered_when_no_backend_is_present() {
      contextRunner
          .withClassLoader(
              new FilteredClassLoader(
                  ObjectMapper.class,
                  com.fasterxml.jackson.databind.ObjectMapper.class,
                  com.google.gson.Gson.class,
                  jakarta.json.bind.Jsonb.class,
                  com.google.protobuf.GeneratedMessage.class))
          .run(context -> assertThat(context).doesNotHaveBean(CodecFactory.class));
    }
  }

  @Nested
  class Fory_is_opt_in {

    @Test
    void an_explicit_thread_safe_fory_bean_wins_over_every_classpath_backend() {
      contextRunner
          .withUserConfiguration(ForyBeanConfig.class)
          .run(
              context -> {
                assertThat(context).hasSingleBean(CodecFactory.class);
                assertThat(context).hasSingleBean(ForyCodecFactory.class);
              });
    }

    @Test
    void fory_on_the_classpath_without_a_bean_changes_nothing() {
      contextRunner.run(
          context -> {
            assertThat(context).hasSingleBean(CodecFactory.class);
            assertThat(context).doesNotHaveBean(ForyCodecFactory.class);
          });
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ForyBeanConfig {

    @Bean
    org.apache.fory.ThreadSafeFory threadSafeFory() {
      return org.apache.fory.Fory.builder()
          .withLanguage(org.apache.fory.config.Language.JAVA)
          .requireClassRegistration(true)
          .buildThreadSafeFory();
    }
  }

  @Nested
  class Backing_off {

    @Test
    void user_defined_codec_factory_wins_over_every_backend() {
      contextRunner
          .withUserConfiguration(CustomCodecFactoryConfig.class)
          .run(
              context -> {
                assertThat(context).hasSingleBean(CodecFactory.class);
                assertThat(context).doesNotHaveBean(JacksonCodecFactory.class);
                assertThat(context).doesNotHaveBean(Jackson2CodecFactory.class);
                assertThat(context).doesNotHaveBean(GsonCodecFactory.class);
                assertThat(context).doesNotHaveBean(ProtobufCodecFactory.class);
              });
    }
  }

  @Nested
  class Object_mapper_reuse {

    @Test
    void jackson_uses_the_object_mapper_bean_when_one_exists() {
      contextRunner
          .withUserConfiguration(CustomObjectMapperConfig.class)
          .run(
              context -> {
                assertThat(context).hasSingleBean(JacksonCodecFactory.class);
                byte[] encoded =
                    context
                        .getBean(JacksonCodecFactory.class)
                        .create(new TypeRef<Map<String, Integer>>() {})
                        .encode(Map.of("a", 1));
                assertThat(new String(encoded, StandardCharsets.UTF_8)).contains("\n");
              });
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomObjectMapperConfig {

    @Bean
    ObjectMapper customObjectMapper() {
      return JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomCodecFactoryConfig {

    @Bean
    CodecFactory customCodecFactory() {
      return new CodecFactory() {
        @Override
        public <T> org.jwcarman.codec.spi.Codec<T> create(TypeRef<T> typeRef) {
          throw new UnsupportedOperationException();
        }
      };
    }
  }
}
