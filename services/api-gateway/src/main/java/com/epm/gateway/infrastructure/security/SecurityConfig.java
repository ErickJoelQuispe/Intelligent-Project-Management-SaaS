package com.epm.gateway.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configures the Gateway as an OAuth2 Resource Server.
 *
 * The Gateway validates the JWT against Keycloak's JWKS (cached locally).
 * If the token is valid, the request is routed to the corresponding microservice.
 * If not, it returns 401 directly — the microservice never sees the request.
 *
 * ReactiveJwtDecoder is declared explicitly because @EnableWebFluxSecurity
 * disables Spring Boot's autoconfiguration for it. The jwks-uri is injected
 * from the environment variable SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKS_URI
 * (set in docker-compose) or falls back to the application.yml value for local dev.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> {})
            .authorizeExchange(exchanges -> exchanges
                // Actuator and health endpoints are public
                .pathMatchers("/actuator/**").permitAll()
                // CORS preflight requests must pass through without auth
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Account registration is public — no JWT required
                .pathMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                // Everything else requires a valid JWT
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
            );
        return http.build();
    }

    /**
     * Explicit ReactiveJwtDecoder bean.
     *
     * Required because @EnableWebFluxSecurity suppresses Spring Boot's
     * ReactiveOAuth2ResourceServerAutoConfiguration. Without this bean,
     * the filter chain fails to initialize even if jwks-uri is set.
     *
     * The decoder downloads Keycloak's public keys once and caches them locally,
     * so JWT validation is local — no HTTP call to Keycloak per request.
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwks-uri}") String jwksUri) {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
    }
}
