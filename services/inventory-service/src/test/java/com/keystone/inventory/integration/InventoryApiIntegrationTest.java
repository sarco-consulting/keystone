package com.keystone.inventory.integration;

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
 * Against a real Postgres, so the unique index backing reserve's idempotency
 * (see V1__init_inventory.sql) and the Flyway-seeded catalog (V2__seed_catalog.sql)
 * are both actually exercised, not assumed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = "outbox.relay.interval-ms=600000")
class InventoryApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void reserveIsIdempotentAndDecrementsStockOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> request = Map.of("orderId", orderId.toString(), "productId", "sku-widget", "quantity", 4);

        ResponseEntity<Map> first = restTemplate.postForEntity("/inventory/reservations", request, Map.class);
        ResponseEntity<Map> replay = restTemplate.postForEntity("/inventory/reservations", request, Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody().get("id")).isEqualTo(first.getBody().get("id"));

        ResponseEntity<Map> item = restTemplate.getForEntity("/inventory/{id}", Map.class, "sku-widget");
        assertThat(item.getBody().get("availableQuantity")).isEqualTo(496);
        assertThat(item.getBody().get("reservedQuantity")).isEqualTo(4);
    }

    @Test
    void reservingMoreThanAvailableReturns409() {
        Map<String, Object> request = Map.of("orderId", UUID.randomUUID().toString(), "productId", "sku-limited-edition", "quantity", 99);

        ResponseEntity<Map> response = restTemplate.postForEntity("/inventory/reservations", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void releaseIsIdempotentAndRestoresStock() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> reserveRequest = Map.of("orderId", orderId.toString(), "productId", "sku-gadget", "quantity", 3);
        restTemplate.postForEntity("/inventory/reservations", reserveRequest, Map.class);

        Map<String, Object> releaseRequest = Map.of("orderId", orderId.toString(), "productId", "sku-gadget");
        ResponseEntity<Void> firstRelease = restTemplate.postForEntity("/inventory/reservations/release", releaseRequest, Void.class);
        ResponseEntity<Void> replayRelease = restTemplate.postForEntity("/inventory/reservations/release", releaseRequest, Void.class);

        assertThat(firstRelease.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayRelease.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> item = restTemplate.getForEntity("/inventory/{id}", Map.class, "sku-gadget");
        assertThat(item.getBody().get("availableQuantity")).isEqualTo(50);
        assertThat(item.getBody().get("reservedQuantity")).isEqualTo(0);
    }
}
