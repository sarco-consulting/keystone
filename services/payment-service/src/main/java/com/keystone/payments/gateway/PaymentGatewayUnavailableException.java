package com.keystone.payments.gateway;

/**
 * Thrown when the circuit is open or retries against the gateway are
 * exhausted — a genuine infrastructure failure, deliberately distinct from a
 * {@code DECLINED} authorization result (see {@link PaymentGatewayResponse}).
 * Conflating the two would cancel orders for "the gateway was down" the same
 * way as "the card was declined," which is a materially different situation
 * for a customer and for retry/support logic upstream.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
