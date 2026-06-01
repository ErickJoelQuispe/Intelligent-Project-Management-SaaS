package com.epm.auth.domain.port.in;

import com.epm.auth.domain.port.in.command.RegisterAccountCommand;
import com.epm.auth.domain.port.in.result.RegisterAccountResult;

/**
 * Driving port: registers a new account.
 *
 * <p>Implemented by the application layer; invoked by REST adapters.
 */
public interface RegisterAccountUseCase {

    /**
     * Registers a new account with the given credentials.
     *
     * @param command registration data
     * @return result containing accountId, keycloakUserId, and email
     * @throws com.epm.auth.domain.exception.DuplicateEmailException   if email already registered
     * @throws com.epm.auth.domain.exception.IdentityProviderException if Keycloak call fails
     */
    RegisterAccountResult register(RegisterAccountCommand command);
}
