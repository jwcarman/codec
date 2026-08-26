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

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for serializing Redis cache values through the application's {@code CodecFactory}.
 *
 * <p>Cache values are heterogeneous but a codec is typed, so each cache that should go through
 * codec names its value type; unlisted caches keep Spring Boot's default serializer. A backend
 * whose format is self-describing (Apache Fory) can serve every cache from one codec for {@code
 * Object} — set {@code default-type} to {@code java.lang.Object} for that.
 */
@ConfigurationProperties(prefix = "codec.redis.cache")
public class CodecRedisCacheProperties {

  /** Whether to configure Redis cache value serialization through codec. */
  private boolean enabled = true;

  /**
   * Fully qualified value type of the codec used for every cache not listed under {@code caches}.
   * Unset by default, which leaves unlisted caches on Spring Boot's serializer.
   */
  private String defaultType;

  /** Cache name to the fully qualified class name of the values it holds. */
  private Map<String, String> caches = new LinkedHashMap<>();

  /**
   * Whether codec serializes Redis cache values.
   *
   * @return {@code true} unless disabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether codec serializes Redis cache values.
   *
   * @param enabled {@code false} to leave every cache on Spring Boot's serializer
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * The value type for caches not listed under {@link #getCaches()}.
   *
   * @return the fully qualified class name, or {@code null} to leave them on Boot's serializer
   */
  public String getDefaultType() {
    return defaultType;
  }

  /**
   * Sets the value type for caches not listed under {@link #getCaches()}.
   *
   * @param defaultType the fully qualified class name, or {@code null}
   */
  public void setDefaultType(String defaultType) {
    this.defaultType = defaultType;
  }

  /**
   * Cache name to the fully qualified class name of the values it holds.
   *
   * @return the mapping; empty by default
   */
  public Map<String, String> getCaches() {
    return caches;
  }

  /**
   * Sets the cache name to value type mapping.
   *
   * @param caches cache name to fully qualified class name
   */
  public void setCaches(Map<String, String> caches) {
    this.caches = caches;
  }
}
