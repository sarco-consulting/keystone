package com.keystone.inventory.application;

import com.keystone.inventory.domain.InventoryItem;
import com.keystone.inventory.domain.InventoryItemNotFoundException;
import com.keystone.inventory.domain.Reservation;
import com.keystone.inventory.domain.ReservationStatus;
import com.keystone.inventory.persistence.InventoryItemRepository;
import com.keystone.inventory.persistence.ReservationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ReservationRepository reservationRepository;

    public InventoryService(InventoryItemRepository inventoryItemRepository, ReservationRepository reservationRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public InventoryItem getItem(String productId) {
        return inventoryItemRepository.findById(productId)
                .orElseThrow(() -> new InventoryItemNotFoundException(productId));
    }

    /**
     * Idempotent: replaying the same (orderId, productId) command returns the
     * existing reservation instead of decrementing stock a second time — this
     * is what makes it safe for an at-least-once command consumer (M3) to
     * call without a separate dedupe check.
     */
    @Transactional
    public Reservation reserve(UUID orderId, String productId, int quantity) {
        return reservationRepository.findByOrderIdAndProductId(orderId, productId)
                .orElseGet(() -> {
                    InventoryItem item = getItem(productId);
                    item.reserve(quantity);
                    inventoryItemRepository.save(item);
                    return reservationRepository.save(new Reservation(orderId, productId, quantity));
                });
    }

    /**
     * Idempotent compensation: a missing or already-released reservation is
     * treated as success, not an error — a saga compensating step must be
     * safe to call more than once.
     */
    @Transactional
    public void release(UUID orderId, String productId) {
        reservationRepository.findByOrderIdAndProductId(orderId, productId).ifPresent(reservation -> {
            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                return;
            }
            InventoryItem item = getItem(productId);
            item.release(reservation.getQuantity());
            inventoryItemRepository.save(item);
            reservation.markReleased();
            reservationRepository.save(reservation);
        });
    }

    /**
     * Reserves every item for an order as one atomic unit: if any item lacks
     * stock, {@link com.keystone.inventory.domain.InsufficientStockException}
     * propagates uncaught and Spring rolls back the whole transaction — any
     * items already reserved earlier in the loop are undone along with it, so
     * an order is never left partially reserved.
     *
     * {@code REQUIRES_NEW} is deliberate, not incidental: the Kafka command
     * listener calls this, catches the exception, and then does more writes
     * of its own (the failure event, the processed-message row) in the same
     * method. If this ran in the listener's ambient transaction instead,
     * Spring would mark that whole shared transaction rollback-only the
     * moment the exception crossed this method's proxy boundary — even
     * though the listener "handles" it — silently discarding those later
     * writes too. A separate transaction means only this unit of work rolls
     * back; the caller's transaction is untouched by it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveAll(UUID orderId, List<ReservationItem> items) {
        for (ReservationItem item : items) {
            reserve(orderId, item.productId(), item.quantity());
        }
    }

    /** Releases every reservation held for an order — the compensating counterpart to {@link #reserveAll}. */
    @Transactional
    public void releaseAll(UUID orderId) {
        for (Reservation reservation : reservationRepository.findByOrderId(orderId)) {
            release(orderId, reservation.getProductId());
        }
    }
}
