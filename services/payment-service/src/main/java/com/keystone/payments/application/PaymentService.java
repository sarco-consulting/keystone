package com.keystone.payments.application;

import com.keystone.payments.domain.PaymentAuthorization;
import com.keystone.payments.domain.PaymentStatus;
import com.keystone.payments.gateway.PaymentGatewayClient;
import com.keystone.payments.gateway.PaymentGatewayResponse;
import com.keystone.payments.persistence.PaymentAuthorizationRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentAuthorizationRepository repository;
    private final PaymentGatewayClient gatewayClient;

    public PaymentService(PaymentAuthorizationRepository repository, PaymentGatewayClient gatewayClient) {
        this.repository = repository;
        this.gatewayClient = gatewayClient;
    }

    /**
     * Idempotent per orderId, same pattern as inventory's reservations: the
     * local table is checked before calling the gateway, so a replayed
     * command never charges twice.
     */
    @Transactional
    public PaymentAuthorization authorize(UUID orderId, BigDecimal amount, String currency) {
        return repository.findByOrderId(orderId).orElseGet(() -> {
            PaymentGatewayResponse response = gatewayClient.authorize(orderId, amount, currency);
            PaymentAuthorization authorization = response.isAuthorized()
                    ? PaymentAuthorization.authorized(orderId, amount, currency, response.reference())
                    : PaymentAuthorization.declined(orderId, amount, currency);
            return repository.save(authorization);
        });
    }

    /**
     * Idempotent compensation: a missing authorization, or one that was
     * declined or already voided, is treated as success.
     */
    @Transactional
    public void voidAuthorization(UUID orderId) {
        repository.findByOrderId(orderId).ifPresent(authorization -> {
            if (authorization.getStatus() != PaymentStatus.AUTHORIZED) {
                return;
            }
            gatewayClient.voidCharge(authorization.getGatewayReference());
            authorization.markVoided();
            repository.save(authorization);
        });
    }
}
