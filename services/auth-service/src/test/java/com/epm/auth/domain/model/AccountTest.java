package com.epm.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.epm.auth.domain.event.AccountRegisteredEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Account} aggregate root.
 */
class AccountTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void registerCreatesAccountWithStatusActive() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void registerGeneratesNonNullAccountId() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        assertThat(account.getId()).isNotNull();
    }

    @Test
    void registerUsesProvidedTenantId() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        assertThat(account.getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void accountIdAndTenantIdAreDifferent() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        assertThat(account.getId()).isNotEqualTo(account.getTenantId());
    }

    @Test
    void registerStoresNormalizedEmail() {
        Account account = Account.register(TENANT_ID, "ALICE@EXAMPLE.COM", "Alice", "Smith");
        assertThat(account.getEmail().value()).isEqualTo("alice@example.com");
    }

    @Test
    void registerDoesNotRaiseAnyDomainEvent() {
        // Event is recorded only after linkKeycloakUser, not at register time.
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        List<Object> events = account.pullDomainEvents();
        assertThat(events).isEmpty();
    }

    @Test
    void pullDomainEventsClearsTheEventsList() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        UUID keycloakId = UUID.randomUUID();
        account.linkKeycloakUser(keycloakId);
        account.pullDomainEvents(); // first pull
        List<Object> secondPull = account.pullDomainEvents();
        assertThat(secondPull).isEmpty();
    }

    // ── linkKeycloakUser ──────────────────────────────────────────────────────

    @Test
    void linkKeycloakUserSetsTheKeycloakUserId() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        UUID keycloakId = UUID.randomUUID();
        account.linkKeycloakUser(keycloakId);
        assertThat(account.getKeycloakUserId()).isEqualTo(keycloakId);
    }

    @Test
    void linkKeycloakUserRecordsOneAccountRegisteredEvent() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        UUID keycloakId = UUID.randomUUID();
        account.linkKeycloakUser(keycloakId);

        List<Object> events = account.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AccountRegisteredEvent.class);
    }

    @Test
    void linkKeycloakUserEventCarriesCorrectKeycloakUserId() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        UUID keycloakId = UUID.randomUUID();
        account.linkKeycloakUser(keycloakId);

        AccountRegisteredEvent event = (AccountRegisteredEvent) account.pullDomainEvents().get(0);
        assertThat(event.keycloakUserId()).isEqualTo(keycloakId);
        assertThat(event.keycloakUserId()).isNotNull();
    }

    @Test
    void linkKeycloakUserEventCarriesCorrectTenantId() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        account.linkKeycloakUser(UUID.randomUUID());

        AccountRegisteredEvent event = (AccountRegisteredEvent) account.pullDomainEvents().get(0);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void linkKeycloakUserWithNullThrowsIllegalArgumentException() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");

        assertThatThrownBy(() -> account.linkKeycloakUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keycloakUserId must not be null");
    }

    @Test
    void linkKeycloakUserCalledTwiceThrowsIllegalStateException() {
        Account account = Account.register(TENANT_ID, "alice@example.com", "Alice", "Smith");
        account.linkKeycloakUser(UUID.randomUUID());

        assertThatThrownBy(() -> account.linkKeycloakUser(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already linked");
    }
}
