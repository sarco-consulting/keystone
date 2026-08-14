package com.keystone.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Guarded by {@code @Version} optimistic locking: concurrent reservations
 * against the same product raise an {@code OptimisticLockingFailureException}
 * rather than silently overselling. Retrying on conflict is left to the
 * caller — a candidate for {@code @Retryable} once contention is observed,
 * not built preemptively here.
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    private long version;

    protected InventoryItem() {
        // JPA
    }

    public InventoryItem(String productId, int availableQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = 0;
    }

    public void reserve(int quantity) {
        if (quantity > availableQuantity) {
            throw new InsufficientStockException(productId, quantity, availableQuantity);
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    public void release(int quantity) {
        int releasable = Math.min(quantity, reservedQuantity);
        reservedQuantity -= releasable;
        availableQuantity += releasable;
    }

    public String getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }
}
