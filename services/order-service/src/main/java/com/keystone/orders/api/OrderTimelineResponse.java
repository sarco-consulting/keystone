package com.keystone.orders.api;

import java.util.List;
import java.util.UUID;

public record OrderTimelineResponse(UUID orderId, List<SagaStepResponse> steps) {
}
