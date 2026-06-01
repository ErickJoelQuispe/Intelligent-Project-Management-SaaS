package com.epm.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SecurityEvent} entity.
 *
 * <p>Tests run RED first — SecurityEvent class does not exist yet at the time of writing.
 */
class SecurityEventTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void logoutFactoryCreatesSecurityEventWithCorrectType() {
        SecurityEvent event = SecurityEvent.logout(TENANT_ID, ACCOUNT_ID, "192.168.1.1", "Mozilla/5.0");
        assertThat(event.eventType()).isEqualTo("LOGOUT");
    }

    @Test
    void logoutFactoryGeneratesNonNullId() {
        SecurityEvent event = SecurityEvent.logout(TENANT_ID, ACCOUNT_ID, "192.168.1.1", "Mozilla/5.0");
        assertThat(event.id()).isNotNull();
    }

    @Test
    void logoutFactoryStoresAccountAndTenantId() {
        SecurityEvent event = SecurityEvent.logout(TENANT_ID, ACCOUNT_ID, "10.0.0.1", "curl/7.0");
        assertThat(event.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void logoutFactoryStoresIpAndUserAgent() {
        SecurityEvent event = SecurityEvent.logout(TENANT_ID, ACCOUNT_ID, "10.0.0.1", "Chrome/100");
        assertThat(event.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(event.userAgent()).isEqualTo("Chrome/100");
    }

    @Test
    void logoutFactoryStoresOccurredAt() {
        SecurityEvent event = SecurityEvent.logout(TENANT_ID, ACCOUNT_ID, "10.0.0.1", "Firefox");
        assertThat(event.occurredAt()).isNotNull();
    }
}
