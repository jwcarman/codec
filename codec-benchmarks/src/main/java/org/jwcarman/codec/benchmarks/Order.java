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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The medium backend payload: an order with a hundred line items — nested records, a list, strings
 * and numbers — so the backends are measured on parser throughput, not per-call overhead.
 *
 * @param id the order id
 * @param customer the customer name
 * @param paid whether the order is paid
 * @param items the line items
 */
public record Order(String id, String customer, boolean paid, List<LineItem> items) {

  /**
   * One line of an order.
   *
   * @param sku the product code
   * @param description the product description
   * @param quantity the quantity ordered
   * @param unitPrice the unit price
   */
  public record LineItem(String sku, String description, int quantity, double unitPrice) {}

  /** The single fixed instance the benchmarks use: 100 deterministic line items. */
  public static final Order SAMPLE = sample();

  private static Order sample() {
    Random random = new Random(99);
    List<LineItem> items = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      items.add(
          new LineItem(
              "SKU-" + (10_000 + random.nextInt(90_000)),
              "Widget model " + (char) ('A' + random.nextInt(26)) + " size " + random.nextInt(50),
              1 + random.nextInt(20),
              Math.round(random.nextDouble() * 10_000) / 100.0));
    }
    return new Order("ord-2026-000123", "Ada Lovelace", true, List.copyOf(items));
  }
}
