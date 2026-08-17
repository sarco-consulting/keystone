package com.keystone.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Without this, Swagger UI's "Authorize" button has nothing to authorize
 * against — SecurityConfig requires a bearer token on every API endpoint.
 * Two alternative schemes are registered: the OAuth2 client-credentials
 * flow lets the UI fetch a token from Keycloak directly (requires the
 * client's Web Origins to include this service's origin — see the
 * "webOrigins" field on infra/keycloak/keystone-realm.json's client, and
 * docs/runbook.md's note on why a running Keycloak container won't pick up
 * realm-file edits without being recreated). The plain bearer scheme is a
 * fallback that always works regardless of Keycloak CORS config: fetch a
 * token via curl (see docs/runbook.md) and paste it in.
 */
@Configuration
public class OpenApiConfig {

    private static final String OAUTH2_SCHEME = "keycloak-client-credentials";
    private static final String BEARER_SCHEME = "bearer-token";

    @Bean
    public OpenAPI openApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        String tokenUrl = issuerUri + "/protocol/openid-connect/token";

        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(OAUTH2_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows().clientCredentials(new OAuthFlow().tokenUrl(tokenUrl))))
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste a token fetched via curl (see docs/runbook.md) — "
                                        + "use this if the OAuth2 flow above fails with a CORS error.")))
                .addSecurityItem(new SecurityRequirement().addList(OAUTH2_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
