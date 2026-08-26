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

import java.util.Map;
import org.jwcarman.codec.redis.CodecRedisSerializer;
import org.jwcarman.codec.spi.CodecFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder;
import org.springframework.util.ClassUtils;

/**
 * Serializes Redis cache values through the application's {@link CodecFactory}. Runs after the
 * backend auto-configurations, so the factory they register is the one used, and before Spring
 * Boot's cache auto-configuration, which collects the {@link RedisCacheManagerBuilderCustomizer}
 * registered here. The customizer keeps every other cache setting — TTL, key prefix, key serializer
 * — from Boot's defaults and replaces only the value serializer, for the caches named in {@link
 * CodecRedisCacheProperties}.
 */
@AutoConfiguration(
    after = {
      ForyCodecAutoConfiguration.class,
      JacksonCodecAutoConfiguration.class,
      Jackson2CodecAutoConfiguration.class,
      GsonCodecAutoConfiguration.class,
      JsonbCodecAutoConfiguration.class,
      ProtobufCodecAutoConfiguration.class
    },
    before = CacheAutoConfiguration.class)
@ConditionalOnClass({
  CodecRedisSerializer.class,
  RedisCacheConfiguration.class,
  RedisCacheManagerBuilderCustomizer.class
})
@ConditionalOnBean(CodecFactory.class)
@ConditionalOnBooleanProperty(name = "codec.redis.cache.enabled", matchIfMissing = true)
@EnableConfigurationProperties(CodecRedisCacheProperties.class)
public class CodecRedisCacheAutoConfiguration {

  @Bean
  public RedisCacheManagerBuilderCustomizer codecRedisCacheManagerBuilderCustomizer(
      CodecFactory factory, CodecRedisCacheProperties properties) {
    return builder -> customize(builder, factory, properties);
  }

  private static void customize(
      RedisCacheManagerBuilder builder,
      CodecFactory factory,
      CodecRedisCacheProperties properties) {
    RedisCacheConfiguration defaults = builder.cacheDefaults();
    if (properties.getDefaultType() != null) {
      defaults = withValues(defaults, factory, properties.getDefaultType());
      builder.cacheDefaults(defaults);
    }
    for (Map.Entry<String, String> cache : properties.getCaches().entrySet()) {
      builder.withCacheConfiguration(
          cache.getKey(), withValues(defaults, factory, cache.getValue()));
    }
  }

  private static RedisCacheConfiguration withValues(
      RedisCacheConfiguration configuration, CodecFactory factory, String typeName) {
    Class<?> type;
    try {
      type = ClassUtils.forName(typeName, CodecRedisCacheAutoConfiguration.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "codec.redis.cache names a value type that is not on the classpath: " + typeName, e);
    }
    return configuration.serializeValuesWith(
        CodecRedisSerializer.of(factory, type).serializationPair());
  }
}
