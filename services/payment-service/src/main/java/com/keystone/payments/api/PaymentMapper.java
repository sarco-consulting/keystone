package com.keystone.payments.api;

import com.keystone.payments.domain.PaymentAuthorization;

final class PaymentMapper {

    private PaymentMapper() {
    }

    static PaymentAuthorizationResponse toResponse(PaymentAuthorization authorization) {
        return new PaymentAuthorizationResponse(
                authorization.getId(),
                authorization.getOrderId(),
                authorization.getAmount(),
                authorization.getCurrency(),
                authorization.getStatus().name(),
                authorization.getGatewayReference(),
                authorization.getCreatedAt(),
                authorization.getUpdatedAt());
    }
}
