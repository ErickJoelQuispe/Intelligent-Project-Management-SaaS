package com.epm.auth.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epm.auth.domain.exception.InvitationTokenAlreadyUsedException;
import com.epm.auth.domain.exception.InvitationTokenExpiredException;
import com.epm.auth.domain.exception.InvitationTokenInvalidException;
import com.epm.auth.domain.port.in.AcceptInvitationUseCase;
import com.epm.auth.domain.port.in.DisableOwnAccountUseCase;
import com.epm.auth.domain.port.in.GetUserSessionsUseCase;
import com.epm.auth.domain.port.in.LogoutAccountUseCase;
import com.epm.auth.domain.port.in.RegisterAccountUseCase;
import com.epm.auth.domain.port.in.RevokeSessionUseCase;
import com.epm.auth.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} for the {@code POST /api/v1/auth/accept-invitation} endpoint.
 *
 * <p>Tests:
 * - Valid request → 201
 * - Token not found → 404
 * - Token expired → 410
 * - Token already used → 409
 * - Missing required field → 400
 * - Endpoint is permit-all (no JWT required)
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "keycloak.server-url=http://localhost:8180",
    "keycloak.realm=epm",
    "keycloak.client-id=epm-backend",
    "keycloak.client-secret=test-secret",
    "eureka.client.enabled=false"
})
class AuthControllerAcceptInvitationTest {

    @Autowired
    private MockMvc mockMvc;

    // All use cases required by AuthController must be mocked
    @MockitoBean
    private RegisterAccountUseCase registerAccountUseCase;

    @MockitoBean
    private LogoutAccountUseCase logoutAccountUseCase;

    @MockitoBean
    private DisableOwnAccountUseCase disableOwnAccountUseCase;

    @MockitoBean
    private GetUserSessionsUseCase getUserSessionsUseCase;

    @MockitoBean
    private RevokeSessionUseCase revokeSessionUseCase;

    @MockitoBean
    private AcceptInvitationUseCase acceptInvitationUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ── Happy path: valid request → 201 ──────────────────────────────────────

    @Test
    void acceptInvitationValidRequestReturns201() throws Exception {
        doNothing().when(acceptInvitationUseCase).accept(any());

        mockMvc.perform(post("/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "valid-token-abc",
                                  "firstName": "John",
                                  "lastName": "Doe",
                                  "password": "securepass"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    // ── Token not found → 404 ─────────────────────────────────────────────────

    @Test
    void acceptInvitationTokenNotFoundReturns404() throws Exception {
        doThrow(new InvitationTokenInvalidException("Invitation token not found"))
                .when(acceptInvitationUseCase).accept(any());

        mockMvc.perform(post("/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "bad-token",
                                  "firstName": "John",
                                  "lastName": "Doe",
                                  "password": "securepass"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    // ── Token expired → 410 ───────────────────────────────────────────────────

    @Test
    void acceptInvitationTokenExpiredReturns410() throws Exception {
        doThrow(new InvitationTokenExpiredException("Invitation token has expired"))
                .when(acceptInvitationUseCase).accept(any());

        mockMvc.perform(post("/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "expired-token",
                                  "firstName": "John",
                                  "lastName": "Doe",
                                  "password": "securepass"
                                }
                                """))
                .andExpect(status().isGone());
    }

    // ── Token already used → 409 ──────────────────────────────────────────────

    @Test
    void acceptInvitationTokenAlreadyUsedReturns409() throws Exception {
        doThrow(new InvitationTokenAlreadyUsedException("Invitation token already used"))
                .when(acceptInvitationUseCase).accept(any());

        mockMvc.perform(post("/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "used-token",
                                  "firstName": "John",
                                  "lastName": "Doe",
                                  "password": "securepass"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    // ── Missing required field → 400 ─────────────────────────────────────────

    @Test
    void acceptInvitationMissingTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "John",
                                  "lastName": "Doe",
                                  "password": "securepass"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── Endpoint is permit-all (no JWT required) ──────────────────────────────

    @Test
    void acceptInvitationDoesNotRequireJwt() throws Exception {
        doNothing().when(acceptInvitationUseCase).accept(any());

        // Request with no Authorization header must still succeed (not 401)
        mockMvc.perform(post("/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "public-token",
                                  "firstName": "Jane",
                                  "lastName": "Doe",
                                  "password": "securepass"
                                }
                                """))
                .andExpect(status().isCreated());
    }
}
