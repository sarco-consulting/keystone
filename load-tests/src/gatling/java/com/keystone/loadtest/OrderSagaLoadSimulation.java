package com.keystone.loadtest;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the real order saga — create, then poll until CONFIRMED — under
 * concurrent load against the already-running "Starting everything" stack
 * (docs/runbook.md), not an ephemeral Testcontainers instance, specifically
 * so the run is observable live on the Grafana dashboard already
 * provisioned in infra/grafana. Targets sku-widget (500 units seeded, see
 * V2__seed_catalog.sql) at a normal price, well clear of the WireMock
 * decline trigger (666.66) and the insufficient-stock SKU. See ADR-0007.
 */
public class OrderSagaLoadSimulation extends Simulation {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String KEYCLOAK_TOKEN_URL = "http://localhost:8180/realms/keystone/protocol/openid-connect/token";
    private static final String CLIENT_ID = "keystone-service-client";
    private static final String CLIENT_SECRET = "keystone-dev-secret";
    private static final int MAX_POLLS = 30;
    private static final String CREATE_ORDER_BODY = """
            {"customerId":"load-test","currency":"USD","items":[{"productId":"sku-widget","quantity":1,"unitPrice":15.00}]}""";

    // Fetched once before injection starts, not per virtual user — this
    // caller represents one trusted service, not one user per token, and a
    // single token comfortably outlives this run (Keycloak's 300s expiry,
    // see infra/keycloak/keystone-realm.json, vs. this simulation's ~60s
    // injection window). No refresh logic — a deliberate scope boundary,
    // not an oversight; see ADR-0007.
    private static final String accessToken = fetchAccessToken();

    private static final HttpProtocolBuilder httpProtocol = http.baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .header("Authorization", "Bearer " + accessToken);

    private static String fetchAccessToken() {
        try {
            String form = "grant_type=client_credentials&client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET;
            HttpRequest request = HttpRequest.newBuilder(URI.create(KEYCLOAK_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Keycloak token fetch failed with status " + response.statusCode()
                        + " — is the stack running? See docs/runbook.md \"Starting everything\".");
            }
            JsonNode body = new ObjectMapper().readTree(response.body());
            return body.get("access_token").asText();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Could not reach Keycloak at " + KEYCLOAK_TOKEN_URL
                    + " — is the stack running? See docs/runbook.md \"Starting everything\".", e);
        }
    }

    private static boolean isTerminal(String status) {
        return "CONFIRMED".equals(status) || "CANCELLED".equals(status);
    }

    private static final ChainBuilder createAndConfirmOrder = group("Saga Completion").on(
            exec(session -> session.set("orderStatus", "PENDING")),
            http("Create Order")
                    .post("/orders")
                    .header("Content-Type", "application/json")
                    .body(StringBody(CREATE_ORDER_BODY))
                    .check(status().is(201), jsonPath("$.id").saveAs("orderId")),
            asLongAs(
                    session -> !isTerminal(session.getString("orderStatus")) && session.getInt("pollCount") < MAX_POLLS,
                    "pollCount",
                    false)
                    .on(
                            pause(Duration.ofMillis(300)),
                            http("Poll Order Status")
                                    .get("/orders/#{orderId}")
                                    .check(status().is(200), jsonPath("$.status").saveAs("orderStatus"))),
            // Spot-check the full step sequence on a fraction of *confirmed*
            // orders — gated on orderStatus, not just a random roll, since
            // checking a still-in-flight order's timeline isn't a
            // correctness bug, it's asking the wrong question at the wrong
            // time (discovered exactly this way on an early run: a couple
            // of orders that hadn't confirmed yet within the poll budget
            // produced a false-negative "steps[5] not found").
            doIf(session -> "CONFIRMED".equals(session.getString("orderStatus"))
                    && ThreadLocalRandom.current().nextInt(10) == 0).then(
                    http("Verify Timeline")
                            .get("/orders/#{orderId}/timeline")
                            .check(status().is(200), jsonPath("$.steps[5].step").is("ORDER_CONFIRMED"))));

    private static final ScenarioBuilder scn = scenario("Order Saga Under Load").exec(createAndConfirmOrder);

    {
        setUp(scn.injectOpen(
                rampUsersPerSec(1).to(5).during(Duration.ofSeconds(10)),
                constantUsersPerSec(5).during(Duration.ofSeconds(40)),
                rampUsersPerSec(5).to(0).during(Duration.ofSeconds(10))))
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95.0).lt(1000),
                        global().successfulRequests().percent().gt(99.0));
    }
}
