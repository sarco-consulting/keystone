package com.keystone.orders.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the real REST API against a real Postgres — no mocks — which is
 * exactly what caught the {@code LazyInitializationException} on
 * {@code GET /orders/{id}} during manual testing (see ADR/README history):
 * a mocked repository would never have exercised the actual fetch-join
 * query or Hibernate's lazy-loading behavior under {@code open-in-view:
 * false}. No Kafka container here deliberately — the saga choreography
 * across services is covered by the end-to-end test in the e2e-tests
 * module; this test is scoped to order-service's own HTTP + persistence
 * layer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
// Pushes the outbox relay's poll interval out past the test's lifetime:
// without a Kafka broker here, letting it fire is harmless but noisy, and
// occasionally races the Postgres container's teardown.
@TestPropertySource(properties = "outbox.relay.interval-ms=600000")
class OrderApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createOrderThenGetItBackReturnsTheSameData() {
        Map<String, Object> request = Map.of(
                "customerId", "customer-integration",
                "currency", "USD",
                "items", java.util.List.of(Map.of("productId", "sku-widget", "quantity", 2, "unitPrice", 12.50)));

        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/orders", request, Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = (String) createResponse.getBody().get("id");

        ResponseEntity<Map> getResponse = restTemplate.getForEntity("/orders/{id}", Map.class, orderId);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("status")).isEqualTo("PENDING");
        assertThat(getResponse.getBody().get("customerId")).isEqualTo("customer-integration");
        assertThat((java.util.List<?>) getResponse.getBody().get("items")).hasSize(1);
    }

    @Test
    void getOrderReturns404ForUnknownId() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/orders/{id}", Map.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createOrderRejectsEmptyItemsWith400() {
        Map<String, Object> request = Map.of("customerId", "customer-integration", "currency", "USD", "items", java.util.List.of());

        ResponseEntity<Map> response = restTemplate.postForEntity("/orders", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
