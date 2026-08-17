# 0005. JWT Authentication via Keycloak (Resource Server Only)

Date: 2026-08-15

## Status

Accepted

## Context

None of the three services had any authentication — every REST endpoint was
wide open. This was the top item in the README's "Production Hardening
Roadmap": "No authentication or authorization on any API... would be the
first thing added for real use (OAuth2/SSO via Keycloak, or at minimum an
API-key check)."

Kafka is the only inter-service channel (no HTTP between services), so this
decision is scoped to inbound REST authentication only, not event-level auth.
There are no human end-users today — every caller is a script, test, or
another automated client — so a machine-to-machine grant is sufficient.

## Decision

Adopt Keycloak as the OIDC/JWT issuer, using the `client_credentials` grant
only (no UI/login flow). Each of the three services is independently
configured as a Spring Security OAuth2 resource server, validating a
token's signature, issuer, and expiry against Keycloak's `issuer-uri` (OIDC
discovery resolves the JWKS endpoint automatically — no separate
`jwk-set-uri` needed).

Each service carries its own small `SecurityConfig`
(`com.keystone.{orders,inventory,payments}.config`) rather than sharing one
through a new `libs/common-security` module. This mirrors the existing
`KafkaErrorHandlingConfig` precedent — a near-identical class already
duplicated across all three services rather than extracted — and keeps
`libs/common-events`, the only existing shared module, free of a concrete
security/web runtime dependency. It would also be the wrong place to build
from: per-endpoint authorization (an explicit non-goal here) will likely need
different role/claim checks per service, which a shared config would fight.

Actuator `health`, `info`, and `prometheus` stay `permitAll()` — Prometheus
scrapes `/actuator/prometheus` and the e2e test's readiness probe polls
`/actuator/health`, both unauthenticated by design. Everything else,
including `/actuator/metrics`, requires a valid bearer token.

A single realm (`keystone`) and a single confidential client
(`keystone-service-client`, hardcoded dev secret `keystone-dev-secret`) cover
every caller — no per-service clients or role differentiation yet.

The realm-export JSON lives once, canonically, at `infra/keycloak/keystone-realm.json`,
consumed two ways: bind-mounted by `infra/docker-compose.yml`, and by
`e2e-tests` via a Gradle `systemProperty` pointing at the same file — the
same mechanism the module already uses for WireMock's stub mappings.

Per-service `@SpringBootTest` integration tests stub the `JwtDecoder` bean
directly rather than standing up a real Keycloak Testcontainer: doing so
three times would exercise the *same* infrastructure behavior redundantly in
each module. Real issuer/JWKS discovery is instead exercised once, with
higher fidelity, by the end-to-end test (a real Keycloak container, a real
client-credentials token) and by the manual docker-compose + curl check.

## Consequences

**Easier**: closes the single biggest gap named in the roadmap, using the
exact technology it named. The approach generalizes cleanly if per-endpoint
authorization is added later — the resource-server wiring doesn't need to
change, only the `authorizeHttpRequests` rules.

**Harder**: local dev now requires Keycloak running before any service
starts cleanly (OIDC discovery happens at boot) or before any non-actuator
endpoint can be called. Every manual curl/Postman workflow needs a
token-fetch step first. `e2e-tests` startup time grows by one more
container. Per-service integration tests need `JwtDecoder`-mock plumbing
duplicated across three test classes.

**Explicitly out of scope** (left for a follow-up):

- Fine-grained per-endpoint authorization, roles, or scopes — this pass only
  validates the token is genuine, not what the caller may do.
- Kafka/event-level authentication or authorization (SASL, mTLS, signed
  payloads) — Kafka remains the trusted internal saga channel.
- mTLS anywhere (service-to-service, service-to-Postgres, service-to-Keycloak).
- Any UI/login/authorization-code flow, refresh tokens, or browser sessions.
- An API gateway or rate limiting.
- Production-grade secret management — the hardcoded dev client secret
  matches the existing `keystone`-password precedent used throughout
  `infra/docker-compose.yml` (e.g. `GF_SECURITY_ADMIN_PASSWORD`).
- Keycloak high availability or persistent storage — `start-dev` with
  ephemeral storage is a dev-only posture, acceptable for this scope.
- Token revocation or introspection — pure local signature/claims validation.
