package com.epm.auth.infrastructure.adapter.out.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.auth.domain.exception.InvitationTokenAlreadyUsedException;
import com.epm.auth.domain.exception.InvitationTokenExpiredException;
import com.epm.auth.domain.exception.InvitationTokenInvalidException;
import com.epm.auth.domain.port.out.InvitationValidationResult;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link FeignInvitationValidationAdapter}.
 *
 * <p>Strict TDD: tests written before implementation.
 * Mocks the {@link UserServiceClient} Feign interface and verifies that HTTP error
 * statuses are translated to the correct domain exceptions.
 */
@ExtendWith(MockitoExtension.class)
class FeignInvitationValidationAdapterTest {

    @Mock
    private UserServiceClient userServiceClient;

    private FeignInvitationValidationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FeignInvitationValidationAdapter(userServiceClient);
    }

    // ── validateToken happy path ──────────────────────────────────────────────

    @Test
    void validateTokenReturnsMappedResult() {
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ValidateInvitationResponse response =
                new ValidateInvitationResponse(invitationId, "user@example.com", tenantId, teamId, "VIEWER");
        when(userServiceClient.validateToken("token-abc")).thenReturn(response);

        InvitationValidationResult result = adapter.validateToken("token-abc");

        assertThat(result.invitationId()).isEqualTo(invitationId);
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.teamId()).isEqualTo(teamId);
        assertThat(result.role()).isEqualTo("VIEWER");
    }

    // ── validateToken maps 404 → InvitationTokenInvalidException ─────────────

    @Test
    void validateToken404MapsToInvalidException() {
        FeignException.NotFound notFound = feignException(404);
        when(userServiceClient.validateToken(any())).thenThrow(notFound);

        assertThatThrownBy(() -> adapter.validateToken("bad-token"))
                .isInstanceOf(InvitationTokenInvalidException.class)
                .hasMessageContaining("not found");
    }

    // ── validateToken maps 410 → InvitationTokenExpiredException ─────────────

    @Test
    void validateToken410MapsToExpiredException() {
        FeignException.Gone gone = feignExceptionGone();
        when(userServiceClient.validateToken(any())).thenThrow(gone);

        assertThatThrownBy(() -> adapter.validateToken("expired-token"))
                .isInstanceOf(InvitationTokenExpiredException.class)
                .hasMessageContaining("expired");
    }

    // ── validateToken maps 409 → InvitationTokenAlreadyUsedException ─────────

    @Test
    void validateToken409MapsToAlreadyUsedException() {
        FeignException.Conflict conflict = feignExceptionConflict();
        when(userServiceClient.validateToken(any())).thenThrow(conflict);

        assertThatThrownBy(() -> adapter.validateToken("used-token"))
                .isInstanceOf(InvitationTokenAlreadyUsedException.class)
                .hasMessageContaining("already used");
    }

    // ── markUsed delegates to client ──────────────────────────────────────────

    @Test
    void markUsedDelegatesToClient() {
        UUID invitationId = UUID.randomUUID();

        adapter.markUsed(invitationId);

        verify(userServiceClient).markUsed(invitationId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String any() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static FeignException.NotFound feignException(int status) {
        return new FeignException.NotFound(
                "404 Not Found",
                feign.Request.create(feign.Request.HttpMethod.GET, "/test", java.util.Collections.emptyMap(),
                        null, java.nio.charset.StandardCharsets.UTF_8, null),
                null,
                java.util.Collections.emptyMap());
    }

    private static FeignException.Gone feignExceptionGone() {
        return new FeignException.Gone(
                "410 Gone",
                feign.Request.create(feign.Request.HttpMethod.GET, "/test", java.util.Collections.emptyMap(),
                        null, java.nio.charset.StandardCharsets.UTF_8, null),
                null,
                java.util.Collections.emptyMap());
    }

    private static FeignException.Conflict feignExceptionConflict() {
        return new FeignException.Conflict(
                "409 Conflict",
                feign.Request.create(feign.Request.HttpMethod.GET, "/test", java.util.Collections.emptyMap(),
                        null, java.nio.charset.StandardCharsets.UTF_8, null),
                null,
                java.util.Collections.emptyMap());
    }
}
