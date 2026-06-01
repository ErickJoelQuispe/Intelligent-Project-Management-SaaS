package com.epm.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.model.SecurityEvent;
import com.epm.auth.domain.port.out.AccountRepository;
import com.epm.auth.domain.port.out.IdentityProviderPort;
import com.epm.auth.domain.port.out.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LogoutAccountUseCaseImpl}.
 *
 * <p>Uses Mockito to stub driven ports. No Spring context needed.
 * Tests run RED first — LogoutAccountUseCaseImpl does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class LogoutAccountUseCaseTest {

    @Mock
    private IdentityProviderPort identityProvider;

    @Mock
    private SecurityEventRepository securityEventRepository;

    @Mock
    private AccountRepository accountRepository;

    private LogoutAccountUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new LogoutAccountUseCaseImpl(identityProvider, securityEventRepository, accountRepository);
    }

    @Test
    void happyPathCallsInvalidateSession() {
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID keycloakUserId = UUID.randomUUID();

        Account account = Account.register("alice@example.com", "Alice", "Smith");
        account.setKeycloakUserId(keycloakUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        useCase.logout(accountId, tenantId, "10.0.0.1", "Mozilla/5.0");

        verify(identityProvider).invalidateSession(keycloakUserId);
    }

    @Test
    void happyPathSavesSecurityEventOfTypeLogout() {
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID keycloakUserId = UUID.randomUUID();

        Account account = Account.register("alice@example.com", "Alice", "Smith");
        account.setKeycloakUserId(keycloakUserId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        useCase.logout(accountId, tenantId, "10.0.0.1", "Chrome/100");

        ArgumentCaptor<SecurityEvent> eventCaptor = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(securityEventRepository).save(eventCaptor.capture());

        SecurityEvent saved = eventCaptor.getValue();
        assertThat(saved.eventType()).isEqualTo("LOGOUT");
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(saved.userAgent()).isEqualTo("Chrome/100");
    }
}
