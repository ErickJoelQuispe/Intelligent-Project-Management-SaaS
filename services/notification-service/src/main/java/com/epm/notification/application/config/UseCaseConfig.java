package com.epm.notification.application.config;

import com.epm.notification.application.usecase.CacheUserEmailService;
import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.application.usecase.NotificationPreferencesService;
import com.epm.notification.domain.port.in.CacheUserEmailUseCase;
import com.epm.notification.domain.port.in.GetPreferencesUseCase;
import com.epm.notification.domain.port.in.UpdatePreferenceUseCase;
import com.epm.notification.domain.port.out.EmailPort;
import com.epm.notification.domain.port.out.NotificationPreferenceRepository;
import com.epm.notification.domain.port.out.NotificationPushPort;
import com.epm.notification.domain.port.out.NotificationRepository;
import com.epm.notification.domain.port.out.UserEmailCacheRepository;
import com.epm.notification.infrastructure.adapter.out.ws.StompNotificationPushAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

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
            NotificationRepository notificationRepository,
            EmailPort emailPort,
            UserEmailCacheRepository userEmailCacheRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            MeterRegistry meterRegistry) {
        return new NotificationApplicationService(
                notificationRepository, emailPort, userEmailCacheRepository,
                notificationPreferenceRepository, meterRegistry);
    }

    @Bean
    NotificationPushPort notificationPushPort(SimpMessagingTemplate messagingTemplate) {
        return new StompNotificationPushAdapter(messagingTemplate);
    }

    @Bean
    CacheUserEmailUseCase cacheUserEmailUseCase(UserEmailCacheRepository userEmailCacheRepository) {
        return new CacheUserEmailService(userEmailCacheRepository);
    }

    @Bean
    GetPreferencesUseCase getPreferencesUseCase(NotificationPreferenceRepository preferenceRepository) {
        return new NotificationPreferencesService(preferenceRepository);
    }

    @Bean
    UpdatePreferenceUseCase updatePreferenceUseCase(NotificationPreferenceRepository preferenceRepository) {
        return new NotificationPreferencesService(preferenceRepository);
    }
}
