package com.epm.project.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.project.domain.exception.DuplicateTeamAssignmentException;
import com.epm.project.domain.exception.ProjectNotFoundException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import com.epm.project.domain.port.in.ArchiveProjectUseCase;
import com.epm.project.domain.port.in.AssignTeamToProjectUseCase;
import com.epm.project.domain.port.in.CreateProjectUseCase;
import com.epm.project.domain.port.in.GetProjectUseCase;
import com.epm.project.domain.port.in.ListProjectsUseCase;
import com.epm.project.domain.port.in.RestoreProjectUseCase;
import com.epm.project.domain.port.in.UpdateProjectUseCase;
import com.epm.project.domain.port.in.result.ProjectMemberResult;
import com.epm.project.domain.port.in.result.ProjectResult;
import com.epm.project.domain.port.in.result.ProjectTeamResult;
import com.epm.project.infrastructure.config.SecurityConfig;
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
 * @WebMvcTest for {@link ProjectController}.
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
class ProjectControllerTest {

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
    private RestoreProjectUseCase restoreProjectUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    // ── POST /api/v1/projects → 201 ──────────────────────────────────────────

    @Test
    void createProject_returns201() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(createProjectUseCase.execute(any())).thenReturn(buildProjectResult(projectId));

        mockMvc.perform(post("/api/v1/projects")
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alpha","description":"desc","visibility":"PRIVATE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alpha Project"));
    }

    // ── GET /api/v1/projects → 200 ───────────────────────────────────────────

    @Test
    void listProjects_returns200() throws Exception {
        when(listProjectsUseCase.execute(any(), any(), eq(false), anyInt(), anyInt()))
                .thenReturn(List.of(buildProjectResult(UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/projects")
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha Project"));
    }

    // ── GET /api/v1/projects with explicit pagination → passes page/size through ──

    @Test
    void listProjects_passesPageAndSizeThrough() throws Exception {
        when(listProjectsUseCase.execute(any(), any(), eq(false), anyInt(), anyInt()))
                .thenReturn(List.of(buildProjectResult(UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/projects")
                        .param("page", "2")
                        .param("size", "50")
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());

        verify(listProjectsUseCase).execute(ownerId, tenantId, false, 2, 50);
    }

    // ── GET /api/v1/projects?page=-1 → 400 (validation) ──────────────────────

    @Test
    void listProjects_returns400_whenNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .param("page", "-1")
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/projects?size=0 → 400 (validation) ───────────────────────

    @Test
    void listProjects_returns400_whenNonPositiveSize() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .param("size", "0")
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/projects/{id} → 200 ─────────────────────────────────────

    @Test
    void getProject_returns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(getProjectUseCase.execute(any(), any(), any(), any()))
                .thenReturn(buildProjectResult(projectId));

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()));
    }

    // ── GET /api/v1/projects/{id} → 404 ─────────────────────────────────────

    @Test
    void getProject_returns404_whenNotFound() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(getProjectUseCase.execute(any(), any(), any(), any()))
                .thenThrow(new ProjectNotFoundException(projectId));

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/projects/{id} → 403 ─────────────────────────────────────

    @Test
    void getProject_returns403_whenUnauthorized() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(getProjectUseCase.execute(any(), any(), any(), any()))
                .thenThrow(new UnauthorizedProjectAccessException(ownerId, projectId));

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/v1/projects/{id} → 200 ───────────────────────────────────

    @Test
    void updateProject_returns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(updateProjectUseCase.execute(any())).thenReturn(buildProjectResult(projectId));

        mockMvc.perform(patch("/api/v1/projects/{id}", projectId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated","description":"new desc","visibility":"TEAM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Project"));
    }

    // ── DELETE /api/v1/projects/{id} → 204 ──────────────────────────────────

    @Test
    void archiveProject_returns204() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/projects/{id}", projectId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/v1/projects/{id}/teams → 201 ──────────────────────────────

    @Test
    void assignTeam_returns201() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{id}/teams", projectId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamId)))
                .andExpect(status().isCreated());
    }

    // ── POST /api/v1/projects/{id}/teams → 409 ──────────────────────────────

    @Test
    void assignTeam_returns409_whenDuplicate() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        doThrow(new DuplicateTeamAssignmentException(teamId, projectId))
                .when(assignTeamToProjectUseCase).execute(any());

        mockMvc.perform(post("/api/v1/projects/{id}/teams", projectId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":"%s"}
                                """.formatted(teamId)))
                .andExpect(status().isConflict());
    }

    // ── No auth header → 401 ─────────────────────────────────────────────────

    @Test
    void noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProjectResult buildProjectResult(UUID projectId) {
        return new ProjectResult(
                projectId,
                "Alpha Project",
                "desc",
                "ACTIVE",
                "PRIVATE",
                ownerId,
                tenantId,
                List.of(new ProjectTeamResult(UUID.randomUUID(), projectId, UUID.randomUUID(), Instant.now())),
                List.of(new ProjectMemberResult(UUID.randomUUID(), projectId, ownerId, "OWNER", Instant.now())),
                Instant.now(),
                Instant.now());
    }
}
