package com.epm.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * User Profile and Team Management Service entry point.
 *
 * <p>Hexagonal architecture — domain, application, and infrastructure layers.
 * OAuth2 Resource Server (Spring MVC), Kafka consumer, and outbox relay.
 */
@SpringBootApplication
@EnableScheduling
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
