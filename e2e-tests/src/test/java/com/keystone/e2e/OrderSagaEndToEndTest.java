package com.keystone.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The flagship regression test: exercises the actual saga — three real
 * service jars, talking real HTTP and real Kafka, against real Postgres and
 * a real (stubbed) payment gateway — the same thing that's been verified by
 * hand throughout this build. Nothing here is mocked; only the *external*
 * payment gateway is a stub, and that's WireMock, not a Java mock.
 *
 * Covers both branches from docs/architecture.md: the happy path and the
 * payment-decline compensation path (release inventory, cancel order).
 */
@Testcontainers
class OrderSagaEndToEndTest {

    private static final Duration SAGA_TIMEOUT = Duration.ofSeconds(30);

    @Container
    static PostgreSQLContainer<?> orderDb =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("order_service").withUsername("keystone").withPassword("keystone");

    @Container
    static PostgreSQLContainer<?> inventoryDb =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("inventory_service").withUsername("keystone").withPassword("keystone");

    @Container
    static PostgreSQLContainer<?> paymentDb =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("payment_service").withUsername("keystone").withPassword("keystone");

    @Container
    static RedpandaContainer redpanda = new RedpandaContainer(
            // docker.io, not docker.redpanda.com — see infra/docker-compose.yml for why.
            DockerImageName.parse("docker.io/redpandadata/redpanda:v24.2.7")
                    .asCompatibleSubstituteFor("docker.redpanda.com/redpandadata/redpanda"));

    @Container
    static GenericContainer<?> wiremock = new GenericContainer<>(DockerImageName.parse("docker.io/wiremock/wiremock:3.9.2"))
            .withExposedPorts(8080)
            .withCommand("--global-response-templating")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(System.getProperty("e2e.wiremock-mappings-dir")), "/home/wiremock/mappings");

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static ManagedService orderService;
    private static ManagedService inventoryService;
    private static ManagedService paymentService;

    @BeforeAll
    static void startServices() throws IOException {
        String bootstrapServers = redpanda.getBootstrapServers().replace("PLAINTEXT://", "");
        String gatewayBaseUrl = "http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080);

        Map<String, String> orderEnv = envOf(dbEnv(orderDb), kafkaEnv(bootstrapServers));
        Map<String, String> inventoryEnv = envOf(dbEnv(inventoryDb), kafkaEnv(bootstrapServers));
        Map<String, String> paymentEnv = envOf(dbEnv(paymentDb), kafkaEnv(bootstrapServers), gatewayEnv(gatewayBaseUrl));

        orderService = ManagedService.start("order-service", System.getProperty("e2e.order-service.jar"), 18081, orderEnv);
        inventoryService = ManagedService.start("inventory-service", System.getProperty("e2e.inventory-service.jar"), 18082, inventoryEnv);
        paymentService = ManagedService.start("payment-service", System.getProperty("e2e.payment-service.jar"), 18083, paymentEnv);

        orderService.waitUntilHealthy(Duration.ofSeconds(60));
        inventoryService.waitUntilHealthy(Duration.ofSeconds(60));
        paymentService.waitUntilHealthy(Duration.ofSeconds(60));
    }

    @AfterAll
    static void stopServices() {
        if (orderService != null) orderService.stop();
        if (inventoryService != null) inventoryService.stop();
        if (paymentService != null) paymentService.stop();
    }

    @Test
    void happyPathReservesPaysAndConfirms() throws Exception {
        String orderId = createOrder("customer-e2e-happy", List.of(Map.of("productId", "sku-widget", "quantity", 2, "unitPrice", 15.00)));

        JsonNode finalOrder = pollUntilTerminalStatus(orderId);

        assertThat(finalOrder.get("status").asText()).isEqualTo("CONFIRMED");

        List<String> steps = timelineSteps(orderId);
        assertThat(steps).containsExactly(
                "ORDER_CREATED", "INVENTORY_RESERVE_REQUESTED", "INVENTORY_RESERVED",
                "PAYMENT_AUTHORIZE_REQUESTED", "PAYMENT_AUTHORIZED", "ORDER_CONFIRMED");
    }

    @Test
    void paymentDeclineCompensatesByReleasingInventoryAndCancelling() throws Exception {
        // 666.66 is WireMock's configured decline trigger — see infra/wiremock/mappings.
        String orderId = createOrder("customer-e2e-decline", List.of(Map.of("productId", "sku-widget", "quantity", 1, "unitPrice", 666.66)));

        JsonNode finalOrder = pollUntilTerminalStatus(orderId);

        assertThat(finalOrder.get("status").asText()).isEqualTo("CANCELLED");

        List<String> steps = timelineSteps(orderId);
        assertThat(steps).containsExactly(
                "ORDER_CREATED", "INVENTORY_RESERVE_REQUESTED", "INVENTORY_RESERVED",
                "PAYMENT_AUTHORIZE_REQUESTED", "PAYMENT_AUTHORIZATION_FAILED",
                "INVENTORY_RELEASE_REQUESTED", "INVENTORY_RELEASED", "ORDER_CANCELLED");
    }

    @SafeVarargs
    private static Map<String, String> envOf(Map<String, String>... fragments) {
        Map<String, String> merged = new HashMap<>();
        for (Map<String, String> fragment : fragments) {
            merged.putAll(fragment);
        }
        return merged;
    }

    private static Map<String, String> dbEnv(PostgreSQLContainer<?> db) {
        Map<String, String> env = new HashMap<>();
        env.put("DB_HOST", db.getHost());
        env.put("DB_PORT", String.valueOf(db.getFirstMappedPort()));
        env.put("DB_USER", db.getUsername());
        env.put("DB_PASSWORD", db.getPassword());
        return env;
    }

    private static Map<String, String> kafkaEnv(String bootstrapServers) {
        return Map.of("KAFKA_BOOTSTRAP_SERVERS", bootstrapServers);
    }

    private static Map<String, String> gatewayEnv(String baseUrl) {
        return Map.of("GATEWAY_BASE_URL", baseUrl);
    }

    private String createOrder(String customerId, List<Map<String, Object>> items) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of("customerId", customerId, "currency", "USD", "items", items);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:18081/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return JSON.readTree(response.body()).get("id").asText();
    }

    private JsonNode pollUntilTerminalStatus(String orderId) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(SAGA_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:18081/orders/" + orderId)).GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode order = JSON.readTree(response.body());
            String status = order.get("status").asText();
            if (status.equals("CONFIRMED") || status.equals("CANCELLED")) {
                return order;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Order " + orderId + " did not reach a terminal status within " + SAGA_TIMEOUT);
    }

    private List<String> timelineSteps(String orderId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:18081/orders/" + orderId + "/timeline")).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode steps = JSON.readTree(response.body()).get("steps");
        return java.util.stream.StreamSupport.stream(steps.spliterator(), false)
                .map(step -> step.get("step").asText())
                .toList();
    }
}
