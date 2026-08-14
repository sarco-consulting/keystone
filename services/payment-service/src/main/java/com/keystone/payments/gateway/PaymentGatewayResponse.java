package com.keystone.payments.gateway;

public record PaymentGatewayResponse(String status, String reference) {

    public boolean isAuthorized() {
        return "AUTHORIZED".equals(status);
    }
}
