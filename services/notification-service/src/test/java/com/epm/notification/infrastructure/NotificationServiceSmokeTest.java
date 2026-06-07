package com.epm.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.port.out.EmailPort;
import com.epm.notification.infrastructure.adapter.out.persistence.NotificationJpaRepository;
import com.epm.notification.infrastructure.adapter.out.persistence.NotificationPreferencesRepositoryAdapter;
import com.epm.notification.infrastructure.adapter.out.persistence.NotificationRepositoryAdapter;
import com.epm.notification.infrastructure.adapter.out.persistence.UserEmailCacheRepositoryAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test to validate the core service wiring (T-C-11).
 *
 * <p>Uses @DataJpaTest to verify DB connectivity and Spring context loads correctly.
 * DB is provided by Testcontainers via AbstractPostgresIT (@ServiceConnection).
 * EmbeddedKafka is not started here — consumer is not loaded in @DataJpaTest scope.
 */
@DataJpaTest
@Import({
    NotificationRepositoryAdapter.class,
    UserEmailCacheRepositoryAdapter.class,
    NotificationPreferencesRepositoryAdapter.class,
    NotificationApplicationService.class
})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class NotificationServiceSmokeTest extends AbstractPostgresIT {

    @MockBean
    private EmailPort emailPort;

    @MockBean
    private MeterRegistry meterRegistry;

    @Autowired
    private NotificationJpaRepository jpaRepository;

    @Autowired
    private NotificationRepositoryAdapter repositoryAdapter;

    @Autowired
    private NotificationPreferencesRepositoryAdapter preferenceRepositoryAdapter;

    @Autowired
    private NotificationApplicationService applicationService;

    /**
     * Validates the persistence layer is correctly wired and Flyway migrations ran (V1–V4).
     * V3 adds notification_preferences; V4 adds user_email_cache.
     */
    @Test
    void smokeTest_dbContextLoadsAndMigrationsApplied() {
        // Flyway migrations V1–V4 must have created the tables — verify by counting (not throwing)
        long count = jpaRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    /**
     * Validates the application service bean is correctly wired with its dependencies.
     */
    @Test
    void smokeTest_applicationServiceIsWiredWithRepository() {
        assertThat(applicationService).isNotNull();
        // listForUser should not throw with a random UUID (just returns empty list)
        java.util.UUID tenantId = java.util.UUID.randomUUID();
        java.util.UUID userId = java.util.UUID.randomUUID();
        java.util.List<com.epm.notification.domain.model.Notification> result =
                applicationService.listForUser(tenantId, userId);
        assertThat(result).isEmpty();
    }
}
