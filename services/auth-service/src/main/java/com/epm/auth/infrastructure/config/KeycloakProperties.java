package com.epm.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keycloak Admin Client configuration properties.
 *
 * <p>Bound from {@code keycloak.*} in application.yml / config-repo/auth-service.yml.
 *
 * @param serverUrl    Keycloak base URL (e.g. http://localhost:8180)
 * @param realm        realm name (e.g. epm)
 * @param clientId     service account client ID (e.g. epm-backend)
 * @param clientSecret service account client secret
 */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
        String serverUrl,
        String realm,
        String clientId,
        String clientSecret) {
}
