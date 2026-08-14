package com.keystone.inventory.persistence;

import com.keystone.inventory.domain.Reservation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByOrderIdAndProductId(UUID orderId, String productId);

    List<Reservation> findByOrderId(UUID orderId);
}
