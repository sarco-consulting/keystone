package com.keystone.inventory.domain;

public class InventoryItemNotFoundException extends RuntimeException {

    public InventoryItemNotFoundException(String productId) {
        super("No inventory item found for product: " + productId);
    }
}
