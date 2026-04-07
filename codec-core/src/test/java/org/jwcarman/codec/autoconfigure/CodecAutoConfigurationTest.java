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

import org.junit.jupiter.api.Test;
import org.jwcarman.codec.builtin.ByteArrayCodec;
import org.jwcarman.codec.builtin.StringCodec;
import org.jwcarman.codec.spi.CodecFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CodecAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CodecAutoConfiguration.class));

  @Test
  void shouldRegisterStringCodecBean() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(StringCodec.class);
        });
  }

  @Test
  void shouldRegisterByteArrayCodecBean() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ByteArrayCodec.class);
        });
  }

  @Test
  void shouldNotRegisterCodecFactoryBean() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(CodecFactory.class);
        });
  }
}
