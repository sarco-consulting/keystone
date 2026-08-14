package com.keystone.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DECLINED is a legitimate outcome of an authorize attempt, not an error —
 * the caller (eventually the saga orchestrator, M4) reads the status rather
 * than catching an exception. Only a downstream infrastructure failure
 * calling the gateway is exceptional.
 */
@Entity
@Table(name = "payment_authorizations")
public class PaymentAuthorization {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "gateway_reference")
    private String gatewayReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentAuthorization() {
        // JPA
    }

    private PaymentAuthorization(UUID orderId, BigDecimal amount, String currency, PaymentStatus status, String gatewayReference) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.gatewayReference = gatewayReference;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PaymentAuthorization authorized(UUID orderId, BigDecimal amount, String currency, String gatewayReference) {
        return new PaymentAuthorization(orderId, amount, currency, PaymentStatus.AUTHORIZED, gatewayReference);
    }

    public static PaymentAuthorization declined(UUID orderId, BigDecimal amount, String currency) {
        return new PaymentAuthorization(orderId, amount, currency, PaymentStatus.DECLINED, null);
    }

    public void markVoided() {
        this.status = PaymentStatus.VOIDED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
