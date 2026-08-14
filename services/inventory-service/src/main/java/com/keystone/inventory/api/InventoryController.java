package com.keystone.inventory.api;

import com.keystone.inventory.application.InventoryService;
import com.keystone.inventory.domain.Reservation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Synchronous internal API for now — the saga orchestrator will drive these
 * same operations asynchronously via Kafka commands from M3 onward. Kept
 * callable directly here too, since it's useful for seeding/inspecting state
 * during development and demos.
 */
@RestController
@RequestMapping("/inventory")
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get current stock for a product")
    public InventoryItemResponse getItem(@PathVariable String productId) {
        return InventoryMapper.toResponse(inventoryService.getItem(productId));
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reserve stock for an order", description = "Idempotent per (orderId, productId).")
    public ReservationResponse reserve(@Valid @RequestBody ReserveInventoryRequest request) {
        Reservation reservation = inventoryService.reserve(request.orderId(), request.productId(), request.quantity());
        return InventoryMapper.toResponse(reservation);
    }

    @PostMapping("/reservations/release")
    @Operation(summary = "Release a reservation (compensation)", description = "Idempotent: a missing or already-released reservation is treated as success.")
    public ResponseEntity<Void> release(@Valid @RequestBody ReleaseInventoryRequest request) {
        inventoryService.release(request.orderId(), request.productId());
        return ResponseEntity.ok().build();
    }
}
