package com.keystone.payments.api;

import com.keystone.payments.application.PaymentService;
import com.keystone.payments.domain.PaymentAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Synchronous internal API for now — the saga orchestrator drives these
 * asynchronously via Kafka commands from M3 onward.
 */
@RestController
@RequestMapping("/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/authorizations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Authorize a payment", description = "Idempotent per orderId. DECLINED is a legitimate result, not an error.")
    public PaymentAuthorizationResponse authorize(@Valid @RequestBody AuthorizePaymentRequest request) {
        PaymentAuthorization authorization = paymentService.authorize(request.orderId(), request.amount(), request.currency());
        return PaymentMapper.toResponse(authorization);
    }

    @PostMapping("/authorizations/void")
    @Operation(summary = "Void a payment authorization (compensation)", description = "Idempotent: a missing, declined, or already-voided authorization is treated as success.")
    public ResponseEntity<Void> voidAuthorization(@Valid @RequestBody VoidPaymentRequest request) {
        paymentService.voidAuthorization(request.orderId());
        return ResponseEntity.ok().build();
    }
}
