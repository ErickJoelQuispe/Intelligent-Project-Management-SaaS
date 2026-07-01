package com.epm.auth.infrastructure.config;

import com.epm.auth.application.usecase.AcceptInvitationUseCaseImpl;
import com.epm.auth.application.usecase.DisableOwnAccountUseCaseImpl;
import com.epm.auth.application.usecase.GetUserSessionsUseCaseImpl;
import com.epm.auth.application.usecase.LogoutAccountUseCaseImpl;
import com.epm.auth.application.usecase.RegisterAccountUseCaseImpl;
import com.epm.auth.application.usecase.RevokeSessionUseCaseImpl;
import com.epm.auth.domain.port.in.AcceptInvitationUseCase;
import com.epm.auth.domain.port.in.DisableOwnAccountUseCase;
import com.epm.auth.domain.port.in.GetUserSessionsUseCase;
import com.epm.auth.domain.port.in.LogoutAccountUseCase;
import com.epm.auth.domain.port.in.RegisterAccountUseCase;
import com.epm.auth.domain.port.in.RevokeSessionUseCase;
import com.epm.auth.domain.port.out.AccountRegistrationTransaction;
import com.epm.auth.domain.port.out.AccountRepository;
import com.epm.auth.domain.port.out.IdentityProviderPort;
import com.epm.auth.domain.port.out.InvitationValidationPort;
import com.epm.auth.domain.port.out.SecurityEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use case implementations with their ports.
 *
 * <p>Use case classes have no Spring annotations — they are pure Java.
 * This @Configuration class is the only place that knows about Spring
 * and performs the dependency injection manually. This keeps the
 * application layer framework-free and easily testable.
 */
@Configuration
class UseCaseConfig {

    @Bean
    RegisterAccountUseCase registerAccountUseCase(
            AccountRepository accountRepository,
            IdentityProviderPort identityProvider,
            AccountRegistrationTransaction registrationTransaction) {
        return new RegisterAccountUseCaseImpl(accountRepository, identityProvider, registrationTransaction);
    }

    @Bean
    LogoutAccountUseCase logoutAccountUseCase(
            IdentityProviderPort identityProvider,
            SecurityEventRepository securityEventRepository,
            AccountRepository accountRepository) {
        return new LogoutAccountUseCaseImpl(identityProvider, securityEventRepository, accountRepository);
    }

    @Bean
    DisableOwnAccountUseCase disableOwnAccountUseCase(IdentityProviderPort identityProvider) {
        return new DisableOwnAccountUseCaseImpl(identityProvider);
    }

    @Bean
    GetUserSessionsUseCase getUserSessionsUseCase(IdentityProviderPort identityProvider) {
        return new GetUserSessionsUseCaseImpl(identityProvider);
    }

    @Bean
    RevokeSessionUseCase revokeSessionUseCase(IdentityProviderPort identityProvider) {
        return new RevokeSessionUseCaseImpl(identityProvider);
    }

    @Bean
    AcceptInvitationUseCase acceptInvitationUseCase(
            InvitationValidationPort invitationValidationPort,
            IdentityProviderPort identityProvider,
            AccountRepository accountRepository,
            AccountRegistrationTransaction registrationTransaction) {
        return new AcceptInvitationUseCaseImpl(
                invitationValidationPort, identityProvider, accountRepository, registrationTransaction);
    }
}
