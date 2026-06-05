package com.epm.ai.infrastructure.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration that registers interceptors.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final RateLimitingInterceptor rateLimitingInterceptor;

    public WebMvcConfig(TenantInterceptor tenantInterceptor,
                        RateLimitingInterceptor rateLimitingInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
        this.rateLimitingInterceptor = rateLimitingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitingInterceptor);
        registry.addInterceptor(tenantInterceptor);
    }
}
