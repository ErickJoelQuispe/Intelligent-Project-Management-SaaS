package com.epm.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.epm.auth.domain.event.AccountRegisteredEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Account} aggregate root.
 *
 * <p>Tests run RED first — Account class does not exist yet at the time of writing.
 */
class AccountTest {

    @Test
    void registerCreatesAccountWithStatusActive() {
        Account account = Account.register("alice@example.com", "Alice", "Smith");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void registerGeneratesNonNullUuidV7AccountId() {
        Account account = Account.register("alice@example.com", "Alice", "Smith");
        assertThat(account.getId()).isNotNull();
    }

    @Test
    void registerGeneratesNonNullUuidV7TenantId() {
        Account account = Account.register("alice@example.com", "Alice", "Smith");
        assertThat(account.getTenantId()).isNotNull();
    }

    @Test
    void accountIdAndTenantIdAreDifferent() {
        Account account = Account.register("alice@example.com", "Alice", "Smith");
        assertThat(account.getId()).isNotEqualTo(account.getTenantId());
    }

    @Test
    void registerStoresNormalizedEmail() {
        Account account = Account.register("ALICE@EXAMPLE.COM", "Alice", "Smith");
        assertThat(account.getEmail().value()).isEqualTo("alice@example.com");
    }

    @Test
    void registerRaisesAccountRegisteredEvent() {
        Account account = Account.register("alice@example.com", "Alice", "Smith");
        List<Object> events = account.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AccountRegisteredEvent.class);
    }

    @Test
    void pullDomainEventsClearsTheEventsList() {
        Account account = Account.register("alice@example.com", "Alice", "Smith");
        account.pullDomainEvents(); // first pull
        List<Object> secondPull = account.pullDomainEvents();
        assertThat(secondPull).isEmpty();
    }
}
