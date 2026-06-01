package com.epm.auth.infrastructure.adapter.in.rest;

import java.util.UUID;

import com.epm.auth.domain.port.in.LogoutAccountUseCase;
import com.epm.auth.domain.port.in.RegisterAccountUseCase;
import com.epm.auth.domain.port.in.command.RegisterAccountCommand;
import com.epm.auth.domain.port.in.result.RegisterAccountResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for authentication operations.
 *
 * <p>Driving port adapter — translates HTTP requests to use case calls.
 * Maps request DTOs to commands and result DTOs from use case results.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterAccountUseCase registerUseCase;
    private final LogoutAccountUseCase logoutUseCase;

    public AuthController(RegisterAccountUseCase registerUseCase, LogoutAccountUseCase logoutUseCase) {
        this.registerUseCase = registerUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    /**
     * Registers a new account.
     *
     * @param request validated registration request
     * @return 201 with account details on success
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterAccountResponse register(@Valid @RequestBody RegisterAccountRequest request) {
        RegisterAccountCommand command = new RegisterAccountCommand(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName());
        RegisterAccountResult result = registerUseCase.register(command);
        return new RegisterAccountResponse(result.accountId(), result.keycloakUserId(), result.email());
    }

    /**
     * Logs out the authenticated user.
     *
     * @param jwt         the authenticated JWT principal
     * @param forwardedFor optional X-Forwarded-For header for IP tracking
     * @param userAgent   optional User-Agent header for security event recording
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        String tenantIdStr = jwt.getClaimAsString("tenant_id");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
        logoutUseCase.logout(accountId, tenantId, forwardedFor, userAgent);
    }
}
