package com.epm.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Service — Spring Boot entry point.
 * Provides AI-assisted task generation, project summarization, and chat.
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
