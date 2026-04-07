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
package org.jwcarman.codec.protobuf;

import com.google.protobuf.GeneratedMessageV3;
import org.jwcarman.codec.autoconfigure.CodecAutoConfiguration;
import org.jwcarman.codec.spi.CodecFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = CodecAutoConfiguration.class)
@ConditionalOnClass(GeneratedMessageV3.class)
@EnableConfigurationProperties(ProtobufCodecProperties.class)
public class ProtobufCodecAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(CodecFactory.class)
  public ProtobufCodecFactory protobufCodecFactory() {
    return new ProtobufCodecFactory();
  }
}
