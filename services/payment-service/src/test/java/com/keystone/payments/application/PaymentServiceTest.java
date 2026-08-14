package com.keystone.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keystone.payments.domain.PaymentAuthorization;
import com.keystone.payments.domain.PaymentStatus;
import com.keystone.payments.gateway.PaymentGatewayClient;
import com.keystone.payments.gateway.PaymentGatewayResponse;
import com.keystone.payments.persistence.PaymentAuthorizationRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentAuthorizationRepository repository;

    @Mock
    private PaymentGatewayClient gatewayClient;

    @Test
    void authorizeIsIdempotentForARepeatedOrderId() {
        PaymentService service = new PaymentService(repository, gatewayClient);
        UUID orderId = UUID.randomUUID();
        PaymentAuthorization existing = PaymentAuthorization.authorized(orderId, BigDecimal.TEN, "USD", "gw-1");
        when(repository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        PaymentAuthorization result = service.authorize(orderId, BigDecimal.TEN, "USD");

        assertThat(result).isSameAs(existing);
        verify(gatewayClient, never()).authorize(any(), any(), any());
    }

    @Test
    void authorizeStoresDeclinedResultWithoutThrowing() {
        PaymentService service = new PaymentService(repository, gatewayClient);
        UUID orderId = UUID.randomUUID();
        when(repository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(gatewayClient.authorize(orderId, BigDecimal.TEN, "USD"))
                .thenReturn(new PaymentGatewayResponse("DECLINED", null));
        when(repository.save(any(PaymentAuthorization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAuthorization result = service.authorize(orderId, BigDecimal.TEN, "USD");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    void voidAuthorizationIsANoOpWhenDeclined() {
        PaymentService service = new PaymentService(repository, gatewayClient);
        UUID orderId = UUID.randomUUID();
        PaymentAuthorization declined = PaymentAuthorization.declined(orderId, BigDecimal.TEN, "USD");
        when(repository.findByOrderId(orderId)).thenReturn(Optional.of(declined));

        service.voidAuthorization(orderId);

        verify(gatewayClient, never()).voidCharge(any());
    }

    @Test
    void voidAuthorizationVoidsAnAuthorizedPayment() {
        PaymentService service = new PaymentService(repository, gatewayClient);
        UUID orderId = UUID.randomUUID();
        PaymentAuthorization authorized = PaymentAuthorization.authorized(orderId, BigDecimal.TEN, "USD", "gw-1");
        when(repository.findByOrderId(orderId)).thenReturn(Optional.of(authorized));

        service.voidAuthorization(orderId);

        assertThat(authorized.getStatus()).isEqualTo(PaymentStatus.VOIDED);
        verify(gatewayClient).voidCharge("gw-1");
    }
}
