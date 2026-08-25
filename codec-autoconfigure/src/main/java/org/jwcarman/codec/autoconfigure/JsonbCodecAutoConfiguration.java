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

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.jwcarman.codec.jsonb.JsonbCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers a {@link JsonbCodecFactory} when the JSON-B backend and API are on the classpath and no
 * other backend has claimed the {@link CodecFactory} slot. Ordered after the Jackson and Gson
 * auto-configurations so the documented precedence holds; reuses the application's {@link Jsonb}
 * bean when one exists (Spring Boot's own JSON-B auto-configuration provides one), otherwise builds
 * a default instance.
 */
@AutoConfiguration(
    after = {
      JacksonCodecAutoConfiguration.class,
      Jackson2CodecAutoConfiguration.class,
      GsonCodecAutoConfiguration.class
    })
@ConditionalOnClass({Jsonb.class, JsonbCodecFactory.class})
public class JsonbCodecAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(CodecFactory.class)
  public JsonbCodecFactory jsonbCodecFactory(ObjectProvider<Jsonb> jsonb) {
    return new JsonbCodecFactory(jsonb.getIfAvailable(JsonbBuilder::create));
  }
}
