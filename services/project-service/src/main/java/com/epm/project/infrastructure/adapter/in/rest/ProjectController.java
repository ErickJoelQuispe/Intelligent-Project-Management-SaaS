package com.epm.project.infrastructure.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.in.ArchiveProjectUseCase;
import com.epm.project.domain.port.in.AssignTeamToProjectUseCase;
import com.epm.project.domain.port.in.CreateProjectUseCase;
import com.epm.project.domain.port.in.GetProjectUseCase;
import com.epm.project.domain.port.in.ListProjectsUseCase;
import com.epm.project.domain.port.in.UpdateProjectUseCase;
import com.epm.project.domain.port.in.command.AssignTeamCommand;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.port.in.command.UpdateProjectCommand;
import com.epm.project.domain.port.in.result.ProjectResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for project operations.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final CreateProjectUseCase createProjectUseCase;
    private final ListProjectsUseCase listProjectsUseCase;
    private final GetProjectUseCase getProjectUseCase;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final ArchiveProjectUseCase archiveProjectUseCase;
    private final AssignTeamToProjectUseCase assignTeamToProjectUseCase;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public ProjectController(CreateProjectUseCase createProjectUseCase,
            ListProjectsUseCase listProjectsUseCase,
            GetProjectUseCase getProjectUseCase,
            UpdateProjectUseCase updateProjectUseCase,
            ArchiveProjectUseCase archiveProjectUseCase,
            AssignTeamToProjectUseCase assignTeamToProjectUseCase,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.createProjectUseCase = createProjectUseCase;
        this.listProjectsUseCase = listProjectsUseCase;
        this.getProjectUseCase = getProjectUseCase;
        this.updateProjectUseCase = updateProjectUseCase;
        this.archiveProjectUseCase = archiveProjectUseCase;
        this.assignTeamToProjectUseCase = assignTeamToProjectUseCase;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    /** POST /api/v1/projects → 201 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProjectRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        ProjectVisibility visibility = parseVisibility(request.visibility());
        ProjectResult result = createProjectUseCase.execute(
                new CreateProjectCommand(request.name(), request.description(), visibility, callerId, tenantId));
        return toResponse(result);
    }

    /** GET /api/v1/projects → 200 */
    @GetMapping
    public List<ProjectResponse> listProjects(@AuthenticationPrincipal Jwt jwt) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return listProjectsUseCase.execute(callerId, tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** GET /api/v1/projects/{id} → 200 */
    @GetMapping("/{id}")
    public ProjectResponse getProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return toResponse(getProjectUseCase.execute(id, callerId, tenantId));
    }

    /** PATCH /api/v1/projects/{id} → 200 */
    @PatchMapping("/{id}")
    public ProjectResponse updateProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        ProjectVisibility visibility = parseVisibility(request.visibility());
        ProjectResult result = updateProjectUseCase.execute(
                new UpdateProjectCommand(id, request.name(), request.description(), visibility, callerId, tenantId));
        return toResponse(result);
    }

    /** DELETE /api/v1/projects/{id} → 204 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        archiveProjectUseCase.execute(id, callerId, tenantId);
    }

    /** POST /api/v1/projects/{id}/teams → 201 */
    @PostMapping("/{id}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public void assignTeam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody AssignTeamRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        assignTeamToProjectUseCase.execute(
                new AssignTeamCommand(id, request.teamId(), callerId, tenantId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProjectResponse toResponse(ProjectResult result) {
        List<TeamAssignmentResponse> teams = result.teams().stream()
                .map(t -> new TeamAssignmentResponse(t.teamId(), t.assignedAt()))
                .toList();
        List<ProjectMemberResponse> members = result.members().stream()
                .map(m -> new ProjectMemberResponse(m.profileId(), m.role(), m.joinedAt()))
                .toList();
        return new ProjectResponse(
                result.id(),
                result.name(),
                result.description(),
                result.status(),
                result.visibility(),
                result.ownerProfileId(),
                result.tenantId(),
                teams,
                members,
                result.createdAt(),
                result.updatedAt());
    }

    private ProjectVisibility parseVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return ProjectVisibility.PRIVATE;
        }
        try {
            return ProjectVisibility.valueOf(visibility.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ProjectVisibility.PRIVATE;
        }
    }
}
