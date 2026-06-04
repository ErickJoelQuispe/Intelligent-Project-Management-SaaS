package com.epm.notification.application.config;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.port.out.NotificationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that wires use case implementations to their port interfaces.
 *
 * <p>Use case implementations are pure Java (no Spring annotations).
 * This configuration class is the only place they are coupled to Spring.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    NotificationApplicationService notificationApplicationService(
            NotificationRepository notificationRepository) {
        return new NotificationApplicationService(notificationRepository);
    }
}
