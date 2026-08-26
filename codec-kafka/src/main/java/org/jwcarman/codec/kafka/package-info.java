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
/**
 * Apache Kafka integration: {@code CodecSerializer}, {@code CodecDeserializer} and {@code
 * CodecSerde} adapt any {@code Codec<T>} to the Kafka client serialization interfaces, for
 * producers, consumers and Kafka Streams.
 */
package org.jwcarman.codec.kafka;
