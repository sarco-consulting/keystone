package com.keystone.orders.api;

import java.time.Instant;

public record SagaStepResponse(String step, String detail, Instant occurredAt) {
}
