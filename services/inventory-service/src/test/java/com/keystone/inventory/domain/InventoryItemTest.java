package com.keystone.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InventoryItemTest {

    @Test
    void reserveMovesQuantityFromAvailableToReserved() {
        InventoryItem item = new InventoryItem("sku-1", 10);

        item.reserve(4);

        assertThat(item.getAvailableQuantity()).isEqualTo(6);
        assertThat(item.getReservedQuantity()).isEqualTo(4);
    }

    @Test
    void reserveRejectsWhenNotEnoughStock() {
        InventoryItem item = new InventoryItem("sku-1", 2);

        assertThatThrownBy(() -> item.reserve(3)).isInstanceOf(InsufficientStockException.class);
        assertThat(item.getAvailableQuantity()).isEqualTo(2);
    }

    @Test
    void releaseMovesQuantityBackToAvailable() {
        InventoryItem item = new InventoryItem("sku-1", 10);
        item.reserve(4);

        item.release(4);

        assertThat(item.getAvailableQuantity()).isEqualTo(10);
        assertThat(item.getReservedQuantity()).isEqualTo(0);
    }
}
