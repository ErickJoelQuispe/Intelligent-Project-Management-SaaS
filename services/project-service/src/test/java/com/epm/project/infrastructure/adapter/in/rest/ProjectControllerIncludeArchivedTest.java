package com.epm.project.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.project.domain.port.in.ArchiveProjectUseCase;
import com.epm.project.domain.port.in.AssignTeamToProjectUseCase;
import com.epm.project.domain.port.in.CreateProjectUseCase;
import com.epm.project.domain.port.in.GetProjectUseCase;
import com.epm.project.domain.port.in.ListProjectsUseCase;
import com.epm.project.domain.port.in.UpdateProjectUseCase;
import com.epm.project.domain.port.in.result.ProjectMemberResult;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.in.result.ProjectTeamResult;
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
 * T-A-01/T-A-02 RED — failing tests for includeArchived param on GET /api/v1/projects.
 *
 * <p>These tests fail until the 3-arg overload on {@link ListProjectsUseCase} and
 * the {@code @RequestParam(defaultValue="false") boolean includeArchived} on
 * {@link ProjectController} are implemented.
 */
@WebMvcTest(ProjectController.class)
@Import({SecurityConfig.class, JwtClaimsExtractor.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false"
})
class ProjectControllerIncludeArchivedTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateProjectUseCase createProjectUseCase;

    @MockitoBean
    private ListProjectsUseCase listProjectsUseCase;

    @MockitoBean
    private GetProjectUseCase getProjectUseCase;

    @MockitoBean
    private UpdateProjectUseCase updateProjectUseCase;

    @MockitoBean
    private ArchiveProjectUseCase archiveProjectUseCase;

    @MockitoBean
    private AssignTeamToProjectUseCase assignTeamToProjectUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    // ── T-A-01: ?includeArchived=true returns archived project ───────────────

    /**
     * T-A-01 — GET /api/v1/projects?includeArchived=true must invoke the 3-arg
     * overload with includeArchived=true and return archived projects.
     */
    @Test
    void listProjects_withIncludeArchivedTrue_returnsArchivedProject() throws Exception {
        UUID archivedProjectId = UUID.randomUUID();
        ProjectResult archivedResult = buildProjectResult(archivedProjectId, "ARCHIVED");

        when(listProjectsUseCase.execute(any(), any(), eq(true), anyInt(), anyInt()))
                .thenReturn(List.of(archivedResult));

        mockMvc.perform(get("/api/v1/projects")
                        .param("includeArchived", "true")
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"))
                .andExpect(jsonPath("$[0].id").value(archivedProjectId.toString()));

        verify(listProjectsUseCase).execute(ownerId, tenantId, true, 0, 20);
    }

    // ── T-A-02: No param (default) excludes archived ─────────────────────────

    /**
     * T-A-02 — GET /api/v1/projects (no param) must invoke the 3-arg overload
     * with includeArchived=false (the default), excluding archived projects.
     */
    @Test
    void listProjects_withoutParam_excludesArchivedByDefault() throws Exception {
        UUID activeProjectId = UUID.randomUUID();
        ProjectResult activeResult = buildProjectResult(activeProjectId, "ACTIVE");

        when(listProjectsUseCase.execute(any(), any(), eq(false), anyInt(), anyInt()))
                .thenReturn(List.of(activeResult));

        mockMvc.perform(get("/api/v1/projects")
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$").isArray());

        verify(listProjectsUseCase).execute(ownerId, tenantId, false, 0, 20);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProjectResult buildProjectResult(UUID projectId, String status) {
        return new ProjectResult(
                projectId,
                "Test Project",
                "desc",
                status,
                "PRIVATE",
                ownerId,
                tenantId,
                List.of(new ProjectTeamResult(UUID.randomUUID(), projectId, UUID.randomUUID(), Instant.now())),
                List.of(new ProjectMemberResult(UUID.randomUUID(), projectId, ownerId, "OWNER", Instant.now())),
                Instant.now(),
                Instant.now());
    }
}
