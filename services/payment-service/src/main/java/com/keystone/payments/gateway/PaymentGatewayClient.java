package com.keystone.payments.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The boundary to the outside world. The only implementation in this repo
 * talks to a WireMock stub (infra/wiremock) — see the Production Hardening
 * Roadmap in the README for what a real gateway integration would add.
 */
public interface PaymentGatewayClient {

    PaymentGatewayResponse authorize(UUID idempotencyKey, BigDecimal amount, String currency);

    void voidCharge(String gatewayReference);
}
