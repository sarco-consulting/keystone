# 0006. Swagger UI Authorize Against Keycloak, With a Manual-Token Fallback

Date: 2026-08-17

## Status

Accepted

## Context

Each service already ships Swagger UI / OpenAPI docs (`springdoc-openapi`).
Once [ADR-0005](0005-jwt-authentication-via-keycloak.md) put every endpoint
behind a bearer-token check, Swagger UI broke in two ways: the docs UI
itself started returning 401 (nothing had permitted `/swagger-ui/**` or
`/v3/api-docs/**`), and even once reachable, "Try it out" had no way to
attach a token — the OpenAPI document had no security scheme describing how
to get one.

## Decision

`/swagger-ui.html`, `/swagger-ui/**`, and `/v3/api-docs/**` are now
`permitAll()` in each service's `SecurityConfig` — the docs/UI must load
without a token; the actual API underneath still requires one.

Each service registers **two alternative** OpenAPI security schemes (new
`OpenApiConfig` per service, same duplicate-rather-than-share reasoning as
ADR-0005):

- `keycloak-client-credentials` — an OAuth2 `clientCredentials` flow
  pointing at Keycloak's token endpoint, letting Swagger UI's Authorize
  button fetch a token itself, in-browser.
- `bearer-token` — a plain HTTP bearer scheme. Fetch a token via curl (as
  already documented for manual testing) and paste it in.

`application.yml` pre-fills the OAuth2 client ID/secret
(`springdoc.swagger-ui.oauth.*`) so the first option is a single click when
it works. Both are registered as alternatives (not one nested inside the
other), so Swagger UI presents them as two independent ways to authorize —
whichever succeeds satisfies every endpoint's lock icon.

**Why both, not just the OAuth2 one:** while wiring this up, the in-browser
Authorize flow appeared to fail with a CORS error even after the Keycloak
client's `webOrigins` was set correctly. The client_credentials-from-browser
pattern is real, but the failure here didn't come from an inherent Keycloak
limitation as first suspected — it came from Keycloak's `--import-realm`
default `IGNORE_EXISTING` strategy: a realm is only imported if one by that
name doesn't already exist yet. A long-running local Keycloak container
whose `keystone` realm was first imported *before* `webOrigins` was added to
`infra/keycloak/keystone-realm.json` silently kept serving the old client
config on every subsequent restart — the file was right, but the running
container never re-read it. (Confirmed by patching the live client via
Keycloak's admin API without recreating the container: the missing
`Access-Control-Allow-Origin` on the actual token response appeared
immediately.) This is now called out explicitly in
[docs/runbook.md](../runbook.md)'s "Diagnosing failures": editing the realm
file requires *recreating* the Keycloak container, not just restarting it.

Given that pitfall is easy to hit again in ordinary local dev (edit the
realm file, forget `--force-recreate`), the plain-bearer scheme stays as a
permanent fallback rather than being removed now that the root cause is
understood — it has no dependency on Keycloak's CORS/realm-import state at
all.

## Consequences

**Easier**: Swagger UI is usable again for manual exploration and testing,
with a reliable fallback that works regardless of Keycloak container state
or browser CORS behavior.

**Harder**: Authorize now shows two schemes instead of one, which needs a
line of explanation (in the scheme's own OpenAPI `description`, and in
docs/runbook.md) so a first-time user knows which one to use if the OAuth2
flow errors. The manual-token path still requires a separate curl step
outside the browser — not a true one-click experience.

**Non-goal**: no attempt was made to make the OAuth2 in-browser flow
bulletproof against every possible Keycloak/CORS misconfiguration; the
bearer fallback exists precisely so that isn't necessary.
