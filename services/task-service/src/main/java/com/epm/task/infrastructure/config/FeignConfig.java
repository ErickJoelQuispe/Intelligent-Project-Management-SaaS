package com.epm.task.infrastructure.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Feign client configuration.
 *
 * <p>Registers a {@link RequestInterceptor} that forwards the {@code Authorization}
 * header from the incoming JWT to outgoing Feign calls.
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor authorizationHeaderInterceptor() {
        return requestTemplate -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                String tokenValue = jwtAuth.getToken().getTokenValue();
                requestTemplate.header("Authorization", "Bearer " + tokenValue);
            }
        };
    }
}
