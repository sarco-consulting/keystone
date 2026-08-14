package com.keystone.payments.persistence;

import com.keystone.payments.domain.PaymentAuthorization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAuthorizationRepository extends JpaRepository<PaymentAuthorization, UUID> {

    Optional<PaymentAuthorization> findByOrderId(UUID orderId);
}
