package com.epm.auth.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.auth.domain.exception.DuplicateEmailException;
import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.domain.model.UserSession;
import com.epm.auth.domain.port.in.DisableOwnAccountUseCase;
import com.epm.auth.domain.port.in.GetUserSessionsUseCase;
import com.epm.auth.domain.port.in.LogoutAccountUseCase;
import com.epm.auth.domain.port.in.RegisterAccountUseCase;
import com.epm.auth.domain.port.in.RevokeSessionUseCase;
import com.epm.auth.domain.port.in.result.RegisterAccountResult;
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
 * @WebMvcTest for {@link AuthController}.
 *
 * Tests all spec scenarios:
 * - POST /register valid body → 201
 * - POST /register duplicate email → 409
 * - POST /register invalid email → 400
 * - POST /register short password → 400
 * - POST /register missing firstName → 400
 * - POST /register Keycloak CB open → 503 with Retry-After header
 * - POST /logout with valid JWT → 204
 * - POST /logout without JWT → 401
 * - DELETE /account with valid JWT → 204
 * - DELETE /account without JWT → 401
 * - DELETE /account when Keycloak fails → 503
 * - GET /sessions → 200 with list
 * - GET /sessions → 200 with empty array
 * - DELETE /sessions/{id} → 204
 * - DELETE /sessions/{id} when adapter throws → 503
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
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

    // Required: provides a mock JwtDecoder so the SecurityConfig (OAuth2 resource server)
    // can initialize without trying to fetch Keycloak's JWKS from the network.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ── Register happy path ─────────────────────────────────────────────────

    @Test
    void registerValidBodyReturns201() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID keycloakId = UUID.randomUUID();
        when(registerAccountUseCase.register(any()))
                .thenReturn(new RegisterAccountResult(accountId, keycloakId, "alice@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "secret123",
                                  "firstName": "Alice",
                                  "lastName": "Smith"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    // ── Register duplicate email → 409 ─────────────────────────────────────

    @Test
    void registerDuplicateEmailReturns409() throws Exception {
        when(registerAccountUseCase.register(any()))
                .thenThrow(new DuplicateEmailException("alice@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "secret123",
                                  "firstName": "Alice",
                                  "lastName": "Smith"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    // ── Register invalid email → 400 ───────────────────────────────────────

    @Test
    void registerInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "secret123",
                                  "firstName": "Alice",
                                  "lastName": "Smith"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── Register short password → 400 ──────────────────────────────────────

    @Test
    void registerShortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "short",
                                  "firstName": "Alice",
                                  "lastName": "Smith"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── Register missing firstName → 400 ───────────────────────────────────

    @Test
    void registerMissingFirstNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "secret123",
                                  "lastName": "Smith"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── Register Keycloak CB open → 503 with Retry-After ───────────────────

    @Test
    void registerKeycloakUnavailableReturns503WithRetryAfter() throws Exception {
        when(registerAccountUseCase.register(any()))
                .thenThrow(new IdentityProviderException("Keycloak unavailable", 30));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "secret123",
                                  "firstName": "Alice",
                                  "lastName": "Smith"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(result ->
                    org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Retry-After")
                    ).isEqualTo("30"));
    }

    // ── Logout with valid JWT → 204 ─────────────────────────────────────────

    @Test
    void logoutWithValidJwtReturns204() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(accountId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .header("X-Forwarded-For", "127.0.0.1")
                        .header("User-Agent", "TestAgent/1.0"))
                .andExpect(status().isNoContent());
    }

    // ── Logout without JWT → 401 ────────────────────────────────────────────

    @Test
    void logoutWithoutJwtReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /account with valid JWT → 204 ────────────────────────────────

    @Test
    void deleteOwnAccountWithValidJwtReturns204() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/auth/account")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isNoContent());
    }

    // ── DELETE /account without JWT → 401 ───────────────────────────────────

    @Test
    void deleteOwnAccountWithoutJwtReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/account"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /account when Keycloak fails → 503 ───────────────────────────

    @Test
    void deleteOwnAccountWhenKeycloakUnavailableReturns503() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new IdentityProviderException("Keycloak unavailable", 30))
                .when(disableOwnAccountUseCase).execute(any());

        mockMvc.perform(delete("/api/v1/auth/account")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(result ->
                    org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Retry-After")
                    ).isEqualTo("30"));
    }

    // ── GET /sessions → 200 with list ────────────────────────────────────────

    @Test
    void getSessionsReturns200WithList() throws Exception {
        UUID userId = UUID.randomUUID();
        List<UserSession> sessions = List.of(
                new UserSession("sid-1", "192.168.1.1",
                        Instant.parse("2024-11-14T20:00:00Z"),
                        Instant.parse("2024-11-14T20:16:40Z")),
                new UserSession("sid-2", "10.0.0.2",
                        Instant.parse("2024-11-14T20:33:20Z"),
                        Instant.parse("2024-11-14T20:50:00Z"))
        );
        when(getUserSessionsUseCase.execute(userId)).thenReturn(sessions);

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("sid-1"))
                .andExpect(jsonPath("$[0].ipAddress").value("192.168.1.1"))
                .andExpect(jsonPath("$[1].sessionId").value("sid-2"));
    }

    // ── GET /sessions → 200 with empty array ─────────────────────────────────

    @Test
    void getSessionsReturns200WithEmptyArray() throws Exception {
        UUID userId = UUID.randomUUID();
        when(getUserSessionsUseCase.execute(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── DELETE /sessions/{id} → 204 ──────────────────────────────────────────

    @Test
    void revokeSessionReturns204() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/auth/sessions/session-abc-123")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isNoContent());
    }

    // ── DELETE /sessions/{id} when adapter throws → 503 ──────────────────────

    @Test
    void revokeSessionWhenKeycloakUnavailableReturns503() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new IdentityProviderException("Keycloak unavailable", 30))
                .when(revokeSessionUseCase).execute(any());

        mockMvc.perform(delete("/api/v1/auth/sessions/session-fail")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
                .andExpect(status().isServiceUnavailable());
    }
}
