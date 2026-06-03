package com.epm.project.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.epm.project.domain.port.in.CheckProjectMembershipUseCase;
import com.epm.project.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T-A-08 RED — failing tests for GET /api/v1/projects/{id}/members/{userId}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>200 is returned when userId is an active member of the project</li>
 *   <li>404 is returned when userId is NOT a member</li>
 * </ul>
 *
 * <p>These tests fail until {@link ProjectMemberController} and
 * {@link CheckProjectMembershipUseCase} are implemented (T-A-09 through T-A-11).
 */
@WebMvcTest(ProjectMemberController.class)
@Import({SecurityConfig.class, JwtClaimsExtractor.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false"
})
class ProjectMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckProjectMembershipUseCase checkProjectMembershipUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID callerId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    // ── T-A-08: 200 when userId is a member ──────────────────────────────────

    /**
     * T-A-08a — Returns 200 with membership response body when the userId is
     * an active member of the project.
     */
    @Test
    void checkMembership_returns200_whenUserIsMember() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(checkProjectMembershipUseCase.isMember(eq(projectId), eq(userId), any()))
                .thenReturn(true);

        mockMvc.perform(get("/api/v1/projects/{id}/members/{userId}", projectId, userId)
                        .with(jwt().jwt(j -> j
                                .subject(callerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member").value(true))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    // ── T-A-08: 404 when userId is NOT a member ───────────────────────────────

    /**
     * T-A-08b — Returns 404 when the userId is NOT an active member of the project.
     * This is the shape needed by the Feign client in task-service.
     */
    @Test
    void checkMembership_returns404_whenUserIsNotMember() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(checkProjectMembershipUseCase.isMember(eq(projectId), eq(userId), any()))
                .thenReturn(false);

        mockMvc.perform(get("/api/v1/projects/{id}/members/{userId}", projectId, userId)
                        .with(jwt().jwt(j -> j
                                .subject(callerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNotFound());
    }
}
