package com.epm.user.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.epm.user.domain.exception.OptimisticLockException;
import com.epm.user.domain.port.in.GetOwnProfileUseCase;
import com.epm.user.domain.port.in.ListTenantUsersUseCase;
import com.epm.user.domain.port.in.UpdateOwnProfileUseCase;
import com.epm.user.domain.port.in.result.UserProfileResult;
import com.epm.user.infrastructure.config.SecurityConfig;
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
 * @WebMvcTest for {@link UserController}.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtClaimsExtractor.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false"
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOwnProfileUseCase getOwnProfileUseCase;

    @MockitoBean
    private UpdateOwnProfileUseCase updateOwnProfileUseCase;

    @MockitoBean
    private ListTenantUsersUseCase listTenantUsersUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ── GET /me with DB profile → 200 + X-Profile-Source: database ─────────

    @Test
    void getOwnProfileWithDatabaseProfileReturns200WithDatabaseSource() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfileResult result = new UserProfileResult(userId, tenantId, "alice@example.com",
                "Alice", "Smith", null, null, 0L, false);
        when(getOwnProfileUseCase.getProfile(any(), any(), any())).thenReturn(result);

        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString())
                                .claim("email", "alice@example.com")
                                .claim("given_name", "Alice")
                                .claim("family_name", "Smith"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Profile-Source", "database"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    // ── GET /me without DB profile → 200 + X-Profile-Source: provisional ────

    @Test
    void getOwnProfileWithNoProfileReturns200WithProvisionalSource() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfileResult result = new UserProfileResult(userId, tenantId, "alice@example.com",
                "Alice", "Smith", null, null, 0L, true);
        when(getOwnProfileUseCase.getProfile(any(), any(), any())).thenReturn(result);

        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString())
                                .claim("email", "alice@example.com")
                                .claim("given_name", "Alice")
                                .claim("family_name", "Smith"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Profile-Source", "provisional"));
    }

    // ── PATCH /me → 200 ──────────────────────────────────────────────────────

    @Test
    void updateOwnProfileReturns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfileResult result = new UserProfileResult(userId, tenantId, "alice@example.com",
                "Bob", "Jones", "Bio", null, 1L, false);
        when(updateOwnProfileUseCase.updateProfile(any(), any(), any())).thenReturn(result);

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Bob",
                                  "lastName": "Jones",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Bob"))
                .andExpect(jsonPath("$.version").value(1));
    }

    // ── PATCH /me version conflict → 409 ─────────────────────────────────────

    @Test
    void updateOwnProfileWithVersionMismatchReturns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(updateOwnProfileUseCase.updateProfile(any(), any(), any()))
                .thenThrow(new OptimisticLockException("version mismatch"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Bob",
                                  "lastName": "Jones",
                                  "version": 99
                                }
                                """))
                .andExpect(status().isConflict());
    }

    // ── GET /me without JWT → 401 ─────────────────────────────────────────────

    @Test
    void getOwnProfileWithoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── FIX C: invalid pagination params → 400 (not 500) ──────────────────────

    @Test
    void listTenantUsersWithNegativePageReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "-1")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTenantUsersWithNegativeSizeReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users")
                        .param("size", "-5")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTenantUsersWithValidDefaultsReturns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(listTenantUsersUseCase.listTenantUsers(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }
}
