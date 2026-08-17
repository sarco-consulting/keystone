package com.keystone.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Against a real Postgres, so the unique index backing reserve's idempotency
 * (see V1__init_inventory.sql) and the Flyway-seeded catalog (V2__seed_catalog.sql)
 * are both actually exercised, not assumed. The {@code JwtDecoder} is stubbed
 * rather than backed by a real Keycloak container — see ADR-0005.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = "outbox.relay.interval-ms=600000")
class InventoryApiIntegrationTest {

    private static final String VALID_TOKEN = "valid-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void stubDecoder() {
        Jwt validJwt = Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "RS256")
                .claim("sub", "keystone-service-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(validJwt);
        when(jwtDecoder.decode(argThat(token -> !VALID_TOKEN.equals(token))))
                .thenThrow(new BadJwtException("invalid token"));
    }

    @Test
    void reserveIsIdempotentAndDecrementsStockOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> request = Map.of("orderId", orderId.toString(), "productId", "sku-widget", "quantity", 4);

        ResponseEntity<Map> first = restTemplate.exchange(
                "/inventory/reservations", HttpMethod.POST, new HttpEntity<>(request, authHeaders()), Map.class);
        ResponseEntity<Map> replay = restTemplate.exchange(
                "/inventory/reservations", HttpMethod.POST, new HttpEntity<>(request, authHeaders()), Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody().get("id")).isEqualTo(first.getBody().get("id"));

        ResponseEntity<Map> item = restTemplate.exchange(
                "/inventory/{id}", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class, "sku-widget");
        assertThat(item.getBody().get("availableQuantity")).isEqualTo(496);
        assertThat(item.getBody().get("reservedQuantity")).isEqualTo(4);
    }

    @Test
    void reservingMoreThanAvailableReturns409() {
        Map<String, Object> request = Map.of("orderId", UUID.randomUUID().toString(), "productId", "sku-limited-edition", "quantity", 99);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/inventory/reservations", HttpMethod.POST, new HttpEntity<>(request, authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void releaseIsIdempotentAndRestoresStock() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> reserveRequest = Map.of("orderId", orderId.toString(), "productId", "sku-gadget", "quantity", 3);
        restTemplate.exchange("/inventory/reservations", HttpMethod.POST, new HttpEntity<>(reserveRequest, authHeaders()), Map.class);

        Map<String, Object> releaseRequest = Map.of("orderId", orderId.toString(), "productId", "sku-gadget");
        ResponseEntity<Void> firstRelease = restTemplate.exchange(
                "/inventory/reservations/release", HttpMethod.POST, new HttpEntity<>(releaseRequest, authHeaders()), Void.class);
        ResponseEntity<Void> replayRelease = restTemplate.exchange(
                "/inventory/reservations/release", HttpMethod.POST, new HttpEntity<>(releaseRequest, authHeaders()), Void.class);

        assertThat(firstRelease.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayRelease.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> item = restTemplate.exchange(
                "/inventory/{id}", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class, "sku-gadget");
        assertThat(item.getBody().get("availableQuantity")).isEqualTo(50);
        assertThat(item.getBody().get("reservedQuantity")).isEqualTo(0);
    }

    @Test
    void reserveWithoutTokenReturns401() {
        Map<String, Object> request = Map.of("orderId", UUID.randomUUID().toString(), "productId", "sku-widget", "quantity", 1);

        ResponseEntity<Map> response = restTemplate.postForEntity("/inventory/reservations", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getItemWithRejectedTokenReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("garbage-token");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/inventory/{id}", HttpMethod.GET, new HttpEntity<>(headers), Map.class, "sku-widget");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(VALID_TOKEN);
        return headers;
    }
}
