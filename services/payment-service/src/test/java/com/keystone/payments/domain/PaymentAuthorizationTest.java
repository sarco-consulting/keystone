package com.keystone.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAuthorizationTest {

    @Test
    void authorizedStartsInAuthorizedStatus() {
        PaymentAuthorization authorization =
                PaymentAuthorization.authorized(UUID.randomUUID(), new BigDecimal("42.00"), "USD", "gw-1");

        assertThat(authorization.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(authorization.getGatewayReference()).isEqualTo("gw-1");
    }

    @Test
    void declinedHasNoGatewayReference() {
        PaymentAuthorization authorization =
                PaymentAuthorization.declined(UUID.randomUUID(), new BigDecimal("42.00"), "USD");

        assertThat(authorization.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(authorization.getGatewayReference()).isNull();
    }

    @Test
    void markVoidedTransitionsFromAuthorized() {
        PaymentAuthorization authorization =
                PaymentAuthorization.authorized(UUID.randomUUID(), new BigDecimal("42.00"), "USD", "gw-1");

        authorization.markVoided();

        assertThat(authorization.getStatus()).isEqualTo(PaymentStatus.VOIDED);
    }
}
