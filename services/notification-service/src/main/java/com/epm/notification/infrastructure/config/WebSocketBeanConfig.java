package com.epm.notification.infrastructure.config;

import com.epm.notification.domain.port.out.NotificationPushPort;
import com.epm.notification.infrastructure.adapter.out.ws.StompNotificationPushAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Infrastructure configuration that wires the WebSocket push adapter to its port.
 *
 * <p>Lives in the infrastructure layer because it creates an infrastructure adapter bean.
 * This keeps {@code application/config/UseCaseConfig} free of infrastructure imports.
 */
@Configuration
public class WebSocketBeanConfig {

    @Bean
    NotificationPushPort notificationPushPort(SimpMessagingTemplate messagingTemplate) {
        return new StompNotificationPushAdapter(messagingTemplate);
    }
}
