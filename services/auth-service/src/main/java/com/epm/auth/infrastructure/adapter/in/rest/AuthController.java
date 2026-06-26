package com.epm.auth.infrastructure.adapter.in.rest;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.epm.auth.domain.model.UserSession;
import com.epm.auth.domain.port.in.DisableOwnAccountUseCase;
import com.epm.auth.domain.port.in.GetUserSessionsUseCase;
import com.epm.auth.domain.port.in.LogoutAccountUseCase;
import com.epm.auth.domain.port.in.RegisterAccountUseCase;
import com.epm.auth.domain.port.in.RevokeSessionUseCase;
import com.epm.auth.domain.port.in.command.RegisterAccountCommand;
import com.epm.auth.domain.port.in.result.RegisterAccountResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final DisableOwnAccountUseCase disableOwnAccountUseCase;
    private final GetUserSessionsUseCase getUserSessionsUseCase;
    private final RevokeSessionUseCase revokeSessionUseCase;

    public AuthController(
            RegisterAccountUseCase registerUseCase,
            LogoutAccountUseCase logoutUseCase,
            DisableOwnAccountUseCase disableOwnAccountUseCase,
            GetUserSessionsUseCase getUserSessionsUseCase,
            RevokeSessionUseCase revokeSessionUseCase) {
        this.registerUseCase = registerUseCase;
        this.logoutUseCase = logoutUseCase;
        this.disableOwnAccountUseCase = disableOwnAccountUseCase;
        this.getUserSessionsUseCase = getUserSessionsUseCase;
        this.revokeSessionUseCase = revokeSessionUseCase;
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

    /**
     * Disables (soft-deletes) the authenticated user's Keycloak account.
     *
     * <p>Step 1 of the frontend-orchestrated account deletion flow.
     * On success returns 204. On Keycloak failure returns 503 with {@code Retry-After: 30}.
     *
     * @param jwt the authenticated JWT principal
     */
    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOwnAccount(@AuthenticationPrincipal Jwt jwt) {
        UUID keycloakUserId = UUID.fromString(jwt.getSubject());
        disableOwnAccountUseCase.execute(keycloakUserId);
    }

    /**
     * Returns all active Keycloak sessions for the authenticated user.
     *
     * @param jwt the authenticated JWT principal
     * @return 200 with list of sessions (may be empty)
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<UserSessionResponse>> getSessions(@AuthenticationPrincipal Jwt jwt) {
        UUID keycloakUserId = UUID.fromString(jwt.getSubject());
        List<UserSession> sessions = getUserSessionsUseCase.execute(keycloakUserId);
        List<UserSessionResponse> responses = sessions.stream()
                .map(s -> new UserSessionResponse(
                        s.sessionId(),
                        s.ipAddress(),
                        DateTimeFormatter.ISO_INSTANT.format(s.started()),
                        DateTimeFormatter.ISO_INSTANT.format(s.lastAccess())
                ))
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Revokes a specific Keycloak session.
     *
     * <p>On success returns 204. On Keycloak failure returns 503 with {@code Retry-After: 30}.
     * If the session has already expired (Keycloak 404), the exception is treated
     * as 204 (success) — the caller should not care about already-expired sessions.
     *
     * @param sessionId the Keycloak session ID to revoke
     * @param jwt       the authenticated JWT principal
     */
    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        revokeSessionUseCase.execute(sessionId);
    }
}
