package com.keystone.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keystone.inventory.domain.InsufficientStockException;
import com.keystone.inventory.domain.InventoryItem;
import com.keystone.inventory.domain.Reservation;
import com.keystone.inventory.domain.ReservationStatus;
import com.keystone.inventory.persistence.InventoryItemRepository;
import com.keystone.inventory.persistence.ReservationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Test
    void reserveIsIdempotentForARepeatedOrderAndProduct() {
        InventoryService service = new InventoryService(inventoryItemRepository, reservationRepository);
        UUID orderId = UUID.randomUUID();
        Reservation existing = new Reservation(orderId, "sku-1", 3);
        when(reservationRepository.findByOrderIdAndProductId(orderId, "sku-1")).thenReturn(Optional.of(existing));

        Reservation result = service.reserve(orderId, "sku-1", 3);

        assertThat(result).isSameAs(existing);
        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void reserveDecrementsStockOnFirstCall() {
        InventoryService service = new InventoryService(inventoryItemRepository, reservationRepository);
        UUID orderId = UUID.randomUUID();
        InventoryItem item = new InventoryItem("sku-1", 10);
        when(reservationRepository.findByOrderIdAndProductId(orderId, "sku-1")).thenReturn(Optional.empty());
        when(inventoryItemRepository.findById("sku-1")).thenReturn(Optional.of(item));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = service.reserve(orderId, "sku-1", 4);

        assertThat(item.getAvailableQuantity()).isEqualTo(6);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        verify(inventoryItemRepository).save(item);
    }

    @Test
    void releaseIsANoOpWhenNoReservationExists() {
        InventoryService service = new InventoryService(inventoryItemRepository, reservationRepository);
        UUID orderId = UUID.randomUUID();
        when(reservationRepository.findByOrderIdAndProductId(orderId, "sku-1")).thenReturn(Optional.empty());

        service.release(orderId, "sku-1");

        verify(inventoryItemRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void releaseRestoresStockAndMarksReservationReleased() {
        InventoryService service = new InventoryService(inventoryItemRepository, reservationRepository);
        UUID orderId = UUID.randomUUID();
        InventoryItem item = new InventoryItem("sku-1", 10);
        item.reserve(4);
        Reservation reservation = new Reservation(orderId, "sku-1", 4);
        when(reservationRepository.findByOrderIdAndProductId(orderId, "sku-1")).thenReturn(Optional.of(reservation));
        when(inventoryItemRepository.findById("sku-1")).thenReturn(Optional.of(item));

        service.release(orderId, "sku-1");

        assertThat(item.getAvailableQuantity()).isEqualTo(10);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(reservationRepository).save(reservation);
    }

    @Test
    void reserveAllReservesEveryItem() {
        InventoryService service = new InventoryService(inventoryItemRepository, reservationRepository);
        UUID orderId = UUID.randomUUID();
        InventoryItem widget = new InventoryItem("sku-widget", 10);
        InventoryItem gadget = new InventoryItem("sku-gadget", 10);
        when(reservationRepository.findByOrderIdAndProductId(any(), any())).thenReturn(Optional.empty());
        when(inventoryItemRepository.findById("sku-widget")).thenReturn(Optional.of(widget));
        when(inventoryItemRepository.findById("sku-gadget")).thenReturn(Optional.of(gadget));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reserveAll(orderId, List.of(new ReservationItem("sku-widget", 3), new ReservationItem("sku-gadget", 2)));

        assertThat(widget.getAvailableQuantity()).isEqualTo(7);
        assertThat(gadget.getAvailableQuantity()).isEqualTo(8);
    }

    @Test
    void reserveAllPropagatesInsufficientStockForAnyItem() {
        InventoryService service = new InventoryService(inventoryItemRepository, reservationRepository);
        UUID orderId = UUID.randomUUID();
        InventoryItem scarce = new InventoryItem("sku-limited-edition", 1);
        when(reservationRepository.findByOrderIdAndProductId(any(), any())).thenReturn(Optional.empty());
        when(inventoryItemRepository.findById("sku-limited-edition")).thenReturn(Optional.of(scarce));

        assertThatThrownBy(() -> service.reserveAll(orderId, List.of(new ReservationItem("sku-limited-edition", 99))))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void releaseAllReleasesEveryReservationForTheOrder() {
        InventoryService service = new InventoryService(inventoryItemRepository, reservationRepository);
        UUID orderId = UUID.randomUUID();
        InventoryItem widget = new InventoryItem("sku-widget", 10);
        widget.reserve(3);
        Reservation reservation = new Reservation(orderId, "sku-widget", 3);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(reservation));
        when(reservationRepository.findByOrderIdAndProductId(orderId, "sku-widget")).thenReturn(Optional.of(reservation));
        when(inventoryItemRepository.findById("sku-widget")).thenReturn(Optional.of(widget));

        service.releaseAll(orderId);

        assertThat(widget.getAvailableQuantity()).isEqualTo(10);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }
}
