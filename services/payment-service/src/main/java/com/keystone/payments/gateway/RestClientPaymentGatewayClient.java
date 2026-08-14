package com.keystone.payments.gateway;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Retry is nested inside CircuitBreaker by Resilience4j's default aspect
 * order — retries happen first, and the breaker only sees the final outcome
 * per call rather than tripping on every individual retry attempt.
 */
@Component
public class RestClientPaymentGatewayClient implements PaymentGatewayClient {

    private final RestClient restClient;

    public RestClientPaymentGatewayClient(RestClient.Builder builder, @Value("${payment.gateway.base-url}") String baseUrl) {
        // Forces HTTP/1.1: the JDK HttpClient's default HTTP/2 upgrade
        // negotiation is flaky against WireMock (intermittent RST_STREAM /
        // EOF), and there's no upside to HTTP/2 for a single mocked gateway.
        HttpClient jdkHttpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.restClient = builder.baseUrl(baseUrl).requestFactory(new JdkClientHttpRequestFactory(jdkHttpClient)).build();
    }

    @Override
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "authorizeFallback")
    @Retry(name = "payment-gateway")
    public PaymentGatewayResponse authorize(UUID idempotencyKey, BigDecimal amount, String currency) {
        ChargeRequest request = new ChargeRequest(amount, currency, idempotencyKey.toString());
        ChargeResponse response = restClient.post()
                .uri("/charges")
                .body(request)
                .retrieve()
                .body(ChargeResponse.class);
        return new PaymentGatewayResponse(response.status(), response.reference());
    }

    @Override
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "voidChargeFallback")
    @Retry(name = "payment-gateway")
    public void voidCharge(String gatewayReference) {
        restClient.post().uri("/charges/{reference}/void", gatewayReference).retrieve().toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private PaymentGatewayResponse authorizeFallback(UUID idempotencyKey, BigDecimal amount, String currency, Throwable t) {
        throw new PaymentGatewayUnavailableException("Payment gateway unavailable after retries", t);
    }

    @SuppressWarnings("unused")
    private void voidChargeFallback(String gatewayReference, Throwable t) {
        throw new PaymentGatewayUnavailableException("Payment gateway unavailable after retries", t);
    }
}
