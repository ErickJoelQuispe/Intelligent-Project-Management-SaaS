package com.epm.user.infrastructure.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.port.in.AddTeamMemberUseCase;
import com.epm.user.domain.port.in.CreateTeamUseCase;
import com.epm.user.domain.port.in.DeleteTeamUseCase;
import com.epm.user.domain.port.in.GetTeamUseCase;
import com.epm.user.domain.port.in.ListTeamsUseCase;
import com.epm.user.domain.port.in.RemoveTeamMemberUseCase;
import com.epm.user.domain.port.in.UpdateTeamMemberRoleUseCase;
import com.epm.user.domain.port.in.UpdateTeamUseCase;
import com.epm.user.domain.port.in.command.AddMemberCommand;
import com.epm.user.domain.port.in.command.CreateTeamCommand;
import com.epm.user.domain.port.in.command.UpdateTeamCommand;
import com.epm.user.domain.port.in.command.UpdateTeamMemberRoleCommand;
import com.epm.user.domain.port.in.result.TeamResult;
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
 * REST adapter for team operations.
 */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final CreateTeamUseCase createTeamUseCase;
    private final ListTeamsUseCase listTeamsUseCase;
    private final GetTeamUseCase getTeamUseCase;
    private final AddTeamMemberUseCase addTeamMemberUseCase;
    private final RemoveTeamMemberUseCase removeTeamMemberUseCase;
    private final DeleteTeamUseCase deleteTeamUseCase;
    private final UpdateTeamUseCase updateTeamUseCase;
    private final UpdateTeamMemberRoleUseCase updateTeamMemberRoleUseCase;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public TeamController(CreateTeamUseCase createTeamUseCase,
            ListTeamsUseCase listTeamsUseCase,
            GetTeamUseCase getTeamUseCase,
            AddTeamMemberUseCase addTeamMemberUseCase,
            RemoveTeamMemberUseCase removeTeamMemberUseCase,
            DeleteTeamUseCase deleteTeamUseCase,
            UpdateTeamUseCase updateTeamUseCase,
            UpdateTeamMemberRoleUseCase updateTeamMemberRoleUseCase,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.createTeamUseCase = createTeamUseCase;
        this.listTeamsUseCase = listTeamsUseCase;
        this.getTeamUseCase = getTeamUseCase;
        this.addTeamMemberUseCase = addTeamMemberUseCase;
        this.removeTeamMemberUseCase = removeTeamMemberUseCase;
        this.deleteTeamUseCase = deleteTeamUseCase;
        this.updateTeamUseCase = updateTeamUseCase;
        this.updateTeamMemberRoleUseCase = updateTeamMemberRoleUseCase;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    /** POST /api/v1/teams → 201 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse createTeam(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTeamRequest request) {
        UUID ownerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        TeamResult result = createTeamUseCase.createTeam(ownerId, tenantId,
                new CreateTeamCommand(request.name(), request.description()));
        return toResponse(result);
    }

    /** GET /api/v1/teams → 200 */
    @GetMapping
    public List<TeamResponse> listTeams(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return listTeamsUseCase.listTeams(userId, tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** GET /api/v1/teams/{teamId} → 200 */
    @GetMapping("/{teamId}")
    public TeamResponse getTeam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID teamId) {
        UUID userId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return toResponse(getTeamUseCase.getTeam(teamId, userId, tenantId));
    }

    /** POST /api/v1/teams/{teamId}/members → 201 */
    @PostMapping("/{teamId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID teamId,
            @Valid @RequestBody AddMemberRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        addTeamMemberUseCase.addMember(teamId, callerId, tenantId,
                new AddMemberCommand(request.userId(), request.role()));
    }

    /** DELETE /api/v1/teams/{teamId}/members/{userId} → 204 */
    @DeleteMapping("/{teamId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID teamId,
            @PathVariable UUID userId) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        removeTeamMemberUseCase.removeMember(teamId, callerId, userId, tenantId);
    }

    /** DELETE /api/v1/teams/{teamId} → 204 */
    @DeleteMapping("/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID teamId) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        deleteTeamUseCase.deleteTeam(teamId, callerId, tenantId);
    }

    /** PATCH /api/v1/teams/{teamId} → 200 */
    @PatchMapping("/{teamId}")
    public TeamResponse updateTeam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID teamId,
            @Valid @RequestBody UpdateTeamRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        TeamResult result = updateTeamUseCase.execute(
                new UpdateTeamCommand(teamId, callerId, tenantId, request.name(), request.description()));
        return toResponse(result);
    }

    /** PATCH /api/v1/teams/{teamId}/members/{userId} → 200 */
    @PatchMapping("/{teamId}/members/{userId}")
    public TeamResponse updateMemberRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID teamId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        TeamResult result = updateTeamMemberRoleUseCase.execute(
                new UpdateTeamMemberRoleCommand(teamId, userId, callerId, tenantId, request.role()));
        return toResponse(result);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TeamResponse toResponse(TeamResult result) {
        List<MemberResponse> members = result.members().stream()
                .map(m -> new MemberResponse(m.userId(), m.role(), m.joinedAt(),
                        m.firstName(), m.lastName(), m.email()))
                .toList();
        return new TeamResponse(result.id(), result.tenantId(), result.ownerId(),
                result.name(), result.description(), members);
    }
}
