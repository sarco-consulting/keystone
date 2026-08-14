package com.keystone.payments.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.keystone.payments.gateway.PaymentGatewayClient;
import com.keystone.payments.gateway.PaymentGatewayResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real Postgres for the idempotency unique-index and persistence behavior;
 * the outbound gateway HTTP call is mocked here rather than run against a
 * second WireMock container — that integration is already covered by the
 * end-to-end test (e2e-tests module) using the real WireMock stub, and
 * duplicating it here would just mean maintaining two copies of the same
 * stub mappings.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = "outbox.relay.interval-ms=600000")
class PaymentApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private PaymentGatewayClient gatewayClient;

    @Test
    void authorizeIsIdempotentAndCallsTheGatewayOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        when(gatewayClient.authorize(any(), any(), any())).thenReturn(new PaymentGatewayResponse("AUTHORIZED", "gw-ref-1"));
        Map<String, Object> request = Map.of("orderId", orderId.toString(), "amount", 42.00, "currency", "USD");

        ResponseEntity<Map> first = restTemplate.postForEntity("/payments/authorizations", request, Map.class);
        ResponseEntity<Map> replay = restTemplate.postForEntity("/payments/authorizations", request, Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("status")).isEqualTo("AUTHORIZED");
        assertThat(replay.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        verify(gatewayClient).authorize(any(), any(), any());
        verifyNoMoreInteractions(gatewayClient);
    }

    @Test
    void authorizeStoresDeclinedResultAsA201NotAnError() {
        when(gatewayClient.authorize(any(), any(), any())).thenReturn(new PaymentGatewayResponse("DECLINED", null));
        Map<String, Object> request = Map.of("orderId", UUID.randomUUID().toString(), "amount", 9999.99, "currency", "USD");

        ResponseEntity<Map> response = restTemplate.postForEntity("/payments/authorizations", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("DECLINED");
        assertThat(response.getBody().get("gatewayReference")).isNull();
    }

    @Test
    void voidIsIdempotentAndCallsGatewayOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        when(gatewayClient.authorize(any(), any(), any())).thenReturn(new PaymentGatewayResponse("AUTHORIZED", "gw-ref-2"));
        restTemplate.postForEntity("/payments/authorizations",
                Map.of("orderId", orderId.toString(), "amount", 15.00, "currency", "USD"), Map.class);

        Map<String, Object> voidRequest = Map.of("orderId", orderId.toString());
        restTemplate.postForEntity("/payments/authorizations/void", voidRequest, Void.class);
        restTemplate.postForEntity("/payments/authorizations/void", voidRequest, Void.class);

        verify(gatewayClient).voidCharge("gw-ref-2");
    }
}
