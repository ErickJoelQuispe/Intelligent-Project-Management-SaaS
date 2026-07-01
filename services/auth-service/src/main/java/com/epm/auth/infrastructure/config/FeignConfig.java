package com.epm.auth.infrastructure.config;

import com.epm.auth.infrastructure.adapter.out.feign.UserServiceClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring Cloud OpenFeign client scanning.
 *
 * <p>Scopes discovery to the feign adapter package to avoid scanning
 * unrelated packages and to keep the configuration explicit.
 */
@Configuration
@EnableFeignClients(clients = UserServiceClient.class)
public class FeignConfig {
}
