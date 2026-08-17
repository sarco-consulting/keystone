package com.keystone.orders.integration;

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
 * Runs the real REST API against a real Postgres — no mocks — which is
 * exactly what caught the {@code LazyInitializationException} on
 * {@code GET /orders/{id}} during manual testing (see ADR/README history):
 * a mocked repository would never have exercised the actual fetch-join
 * query or Hibernate's lazy-loading behavior under {@code open-in-view:
 * false}. No Kafka container here deliberately — the saga choreography
 * across services is covered by the end-to-end test in the e2e-tests
 * module; this test is scoped to order-service's own HTTP + persistence
 * layer. Likewise, the {@code JwtDecoder} is stubbed rather than backed by
 * a real Keycloak container — issuer/JWKS discovery is exercised once,
 * with higher fidelity, by the end-to-end test instead (see ADR-0005).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
// Pushes the outbox relay's poll interval out past the test's lifetime:
// without a Kafka broker here, letting it fire is harmless but noisy, and
// occasionally races the Postgres container's teardown.
@TestPropertySource(properties = "outbox.relay.interval-ms=600000")
class OrderApiIntegrationTest {

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
    void createOrderThenGetItBackReturnsTheSameData() {
        Map<String, Object> request = Map.of(
                "customerId", "customer-integration",
                "currency", "USD",
                "items", java.util.List.of(Map.of("productId", "sku-widget", "quantity", 2, "unitPrice", 12.50)));

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/orders", HttpMethod.POST, new HttpEntity<>(request, authHeaders()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = (String) createResponse.getBody().get("id");

        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/orders/{id}", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class, orderId);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("status")).isEqualTo("PENDING");
        assertThat(getResponse.getBody().get("customerId")).isEqualTo("customer-integration");
        assertThat((java.util.List<?>) getResponse.getBody().get("items")).hasSize(1);
    }

    @Test
    void getOrderReturns404ForUnknownId() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/orders/{id}", HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createOrderRejectsEmptyItemsWith400() {
        Map<String, Object> request = Map.of("customerId", "customer-integration", "currency", "USD", "items", java.util.List.of());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/orders", HttpMethod.POST, new HttpEntity<>(request, authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getOrderWithoutTokenReturns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/orders/{id}", Map.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getOrderWithRejectedTokenReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("garbage-token");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/orders/{id}", HttpMethod.GET, new HttpEntity<>(headers), Map.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(VALID_TOKEN);
        return headers;
    }
}
