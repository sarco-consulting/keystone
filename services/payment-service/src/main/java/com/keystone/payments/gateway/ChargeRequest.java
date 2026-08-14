package com.keystone.payments.gateway;

import java.math.BigDecimal;

record ChargeRequest(BigDecimal amount, String currency, String idempotencyKey) {
}
