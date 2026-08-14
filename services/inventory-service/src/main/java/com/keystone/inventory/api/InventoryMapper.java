package com.keystone.inventory.api;

import com.keystone.inventory.domain.InventoryItem;
import com.keystone.inventory.domain.Reservation;

final class InventoryMapper {

    private InventoryMapper() {
    }

    static InventoryItemResponse toResponse(InventoryItem item) {
        return new InventoryItemResponse(item.getProductId(), item.getAvailableQuantity(), item.getReservedQuantity());
    }

    static ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getProductId(),
                reservation.getQuantity(),
                reservation.getStatus().name(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
