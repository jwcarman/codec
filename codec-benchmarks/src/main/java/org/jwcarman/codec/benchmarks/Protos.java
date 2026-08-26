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
package org.jwcarman.codec.benchmarks;

/** Converts the benchmark records to their Protobuf messages. */
final class Protos {

  private Protos() {}

  static BenchProtos.PersonProto person(Person person) {
    return BenchProtos.PersonProto.newBuilder()
        .setName(person.name())
        .setAge(person.age())
        .setActive(person.active())
        .setEmail(person.email())
        .build();
  }

  static BenchProtos.OrderProto order(Order order) {
    BenchProtos.OrderProto.Builder builder =
        BenchProtos.OrderProto.newBuilder()
            .setId(order.id())
            .setCustomer(order.customer())
            .setPaid(order.paid());
    for (Order.LineItem item : order.items()) {
      builder.addItems(
          BenchProtos.LineItemProto.newBuilder()
              .setSku(item.sku())
              .setDescription(item.description())
              .setQuantity(item.quantity())
              .setUnitPrice(item.unitPrice()));
    }
    return builder.build();
  }
}
