package com.keystone.payments.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VoidPaymentRequest(@NotNull UUID orderId) {
}
