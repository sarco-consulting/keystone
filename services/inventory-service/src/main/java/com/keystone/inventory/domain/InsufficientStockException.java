package com.keystone.inventory.domain;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productId, int requested, int available) {
        super("Insufficient stock for %s: requested %d, available %d".formatted(productId, requested, available));
    }
}
