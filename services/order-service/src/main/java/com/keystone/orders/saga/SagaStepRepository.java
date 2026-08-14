package com.keystone.orders.saga;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaStepRepository extends JpaRepository<SagaStep, UUID> {

    List<SagaStep> findByOrderIdOrderByOccurredAtAsc(UUID orderId);
}
