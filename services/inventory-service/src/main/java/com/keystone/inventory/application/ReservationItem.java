package com.keystone.inventory.application;

/** Input to {@link InventoryService#reserveAll}, kept independent of the Kafka command contract. */
public record ReservationItem(String productId, int quantity) {
}
