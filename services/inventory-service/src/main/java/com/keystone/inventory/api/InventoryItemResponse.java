package com.keystone.inventory.api;

public record InventoryItemResponse(String productId, int availableQuantity, int reservedQuantity) {
}
