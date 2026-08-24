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

import com.google.protobuf.GeneratedMessage;
import org.jwcarman.codec.protobuf.ProtobufCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures a {@link ProtobufCodecFactory} when the Protocol Buffers backend is on the
 * classpath and no other {@link CodecFactory} bean exists. Lowest-precedence backend.
 */
@AutoConfiguration(
    after = {
      JacksonCodecAutoConfiguration.class,
      Jackson2CodecAutoConfiguration.class,
      GsonCodecAutoConfiguration.class
    })
@ConditionalOnClass({GeneratedMessage.class, ProtobufCodecFactory.class})
public class ProtobufCodecAutoConfiguration {

  /**
   * Creates the factory.
   *
   * @return the codec factory
   */
  @Bean
  @ConditionalOnMissingBean(CodecFactory.class)
  public ProtobufCodecFactory protobufCodecFactory() {
    return new ProtobufCodecFactory();
  }
}
