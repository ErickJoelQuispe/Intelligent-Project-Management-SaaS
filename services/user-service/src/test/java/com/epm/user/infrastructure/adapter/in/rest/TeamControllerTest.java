package com.epm.user.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

import com.epm.user.domain.exception.DuplicateMemberException;
import com.epm.user.domain.exception.LastOwnerException;
import com.epm.user.domain.exception.SelfRoleChangeException;
import com.epm.user.domain.exception.TeamNotFoundException;
import com.epm.user.domain.exception.UnauthorizedException;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.in.AddTeamMemberUseCase;
import com.epm.user.domain.port.in.CreateTeamUseCase;
import com.epm.user.domain.port.in.DeleteTeamUseCase;
import com.epm.user.domain.port.in.GetTeamUseCase;
import com.epm.user.domain.port.in.ListTeamsUseCase;
import com.epm.user.domain.port.in.RemoveTeamMemberUseCase;
import com.epm.user.domain.port.in.UpdateTeamMemberRoleUseCase;
import com.epm.user.domain.port.in.UpdateTeamUseCase;
import com.epm.user.domain.port.in.result.MemberResult;
import com.epm.user.domain.port.in.result.TeamResult;
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
 * @WebMvcTest for {@link TeamController}.
 */
@WebMvcTest(TeamController.class)
@Import({SecurityConfig.class, JwtClaimsExtractor.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false"
})
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTeamUseCase createTeamUseCase;

    @MockitoBean
    private ListTeamsUseCase listTeamsUseCase;

    @MockitoBean
    private GetTeamUseCase getTeamUseCase;

    @MockitoBean
    private AddTeamMemberUseCase addTeamMemberUseCase;

    @MockitoBean
    private RemoveTeamMemberUseCase removeTeamMemberUseCase;

    @MockitoBean
    private DeleteTeamUseCase deleteTeamUseCase;

    @MockitoBean
    private UpdateTeamUseCase updateTeamUseCase;

    @MockitoBean
    private UpdateTeamMemberRoleUseCase updateTeamMemberRoleUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UUID ownerId = UUID.randomUUID();
    private UUID tenantId = UUID.randomUUID();

    // ── POST /teams → 201 ────────────────────────────────────────────────────

    @Test
    void createTeamReturns201() throws Exception {
        UUID teamId = UUID.randomUUID();
        MemberResult member = new MemberResult(ownerId, TeamRole.OWNER, Instant.now(), null, null, null);
        TeamResult result = new TeamResult(teamId, tenantId, ownerId, "Alpha Team", "desc", List.of(member));
        when(createTeamUseCase.createTeam(any(), any(), any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/teams")
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alpha Team",
                                  "description": "desc"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alpha Team"));
    }

    // ── GET /teams → 200 ─────────────────────────────────────────────────────

    @Test
    void listTeamsReturns200() throws Exception {
        UUID teamId = UUID.randomUUID();
        MemberResult member = new MemberResult(ownerId, TeamRole.OWNER, Instant.now(), null, null, null);
        when(listTeamsUseCase.listTeams(any(), any())).thenReturn(
                List.of(new TeamResult(teamId, tenantId, ownerId, "Alpha", null, List.of(member))));

        mockMvc.perform(get("/api/v1/teams")
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha"));
    }

    // ── GET /teams/{id} → 200 ────────────────────────────────────────────────

    @Test
    void getTeamReturns200() throws Exception {
        UUID teamId = UUID.randomUUID();
        MemberResult member = new MemberResult(ownerId, TeamRole.OWNER, Instant.now(), null, null, null);
        when(getTeamUseCase.getTeam(any(), any(), any())).thenReturn(
                new TeamResult(teamId, tenantId, ownerId, "Alpha", null, List.of(member)));

        mockMvc.perform(get("/api/v1/teams/{id}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha"));
    }

    // ── GET /teams/{id} not found → 404 ──────────────────────────────────────

    @Test
    void getTeamNotFoundReturns404() throws Exception {
        UUID teamId = UUID.randomUUID();
        when(getTeamUseCase.getTeam(any(), any(), any()))
                .thenThrow(new TeamNotFoundException(teamId));

        mockMvc.perform(get("/api/v1/teams/{id}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNotFound());
    }

    // ── POST /teams/{id}/members → 201 ───────────────────────────────────────

    @Test
    void addMemberReturns201() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/teams/{teamId}/members", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s",
                                  "role": "MEMBER"
                                }
                                """.formatted(memberId)))
                .andExpect(status().isCreated());
    }

    // ── POST /teams/{id}/members duplicate → 409 ─────────────────────────────

    @Test
    void addDuplicateMemberReturns409() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        doThrow(new DuplicateMemberException(memberId))
                .when(addTeamMemberUseCase).addMember(any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/teams/{teamId}/members", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s",
                                  "role": "MEMBER"
                                }
                                """.formatted(memberId)))
                .andExpect(status().isConflict());
    }

    // ── DELETE /teams/{id}/members/{uid} → 204 ───────────────────────────────

    @Test
    void removeMemberReturns204() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/teams/{teamId}/members/{userId}", teamId, memberId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNoContent());
    }

    // ── DELETE /teams/{id}/members/{uid} last owner → 409 ────────────────────

    @Test
    void removeLastOwnerReturns409() throws Exception {
        UUID teamId = UUID.randomUUID();
        doThrow(new LastOwnerException())
                .when(removeTeamMemberUseCase).removeMember(any(), any(), any(), any());

        mockMvc.perform(delete("/api/v1/teams/{teamId}/members/{userId}", teamId, ownerId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isConflict());
    }

    // ── DELETE /teams/{id} → 204 ──────────────────────────────────────────────

    @Test
    void deleteTeamReturns204() throws Exception {
        UUID teamId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/teams/{teamId}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNoContent());
    }

    // ── DELETE /teams/{id} not found → 404 ───────────────────────────────────

    @Test
    void deleteTeamNotFoundReturns404() throws Exception {
        UUID teamId = UUID.randomUUID();
        doThrow(new TeamNotFoundException(teamId))
                .when(deleteTeamUseCase).deleteTeam(any(), any(), any());

        mockMvc.perform(delete("/api/v1/teams/{teamId}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /teams/{id} non-owner → 403 ───────────────────────────────────

    @Test
    void deleteTeamForbiddenReturns403() throws Exception {
        UUID teamId = UUID.randomUUID();
        doThrow(new UnauthorizedException("Only team owners can delete a team"))
                .when(deleteTeamUseCase).deleteTeam(any(), any(), any());

        mockMvc.perform(delete("/api/v1/teams/{teamId}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /teams/{id} → 200 ───────────────────────────────────────────────

    @Test
    void patchTeamReturns200() throws Exception {
        UUID teamId = UUID.randomUUID();
        MemberResult member = new MemberResult(ownerId, TeamRole.OWNER, Instant.now(), null, null, null);
        TeamResult result = new TeamResult(teamId, tenantId, ownerId, "New Name", "desc", List.of(member));
        when(updateTeamUseCase.execute(any())).thenReturn(result);

        mockMvc.perform(patch("/api/v1/teams/{teamId}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "New Name" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    // ── PATCH /teams/{id} blank name → 400 ───────────────────────────────────

    @Test
    void patchTeamBlankNameReturns400() throws Exception {
        UUID teamId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/teams/{teamId}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "" }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /teams/{id} no fields → 400 ────────────────────────────────────

    @Test
    void patchTeamNoFieldsReturns400() throws Exception {
        UUID teamId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/teams/{teamId}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /teams/{id} non-owner → 403 ────────────────────────────────────

    @Test
    void patchTeamForbiddenReturns403() throws Exception {
        UUID teamId = UUID.randomUUID();
        when(updateTeamUseCase.execute(any()))
                .thenThrow(new UnauthorizedException("Only team owners can update the team"));

        mockMvc.perform(patch("/api/v1/teams/{teamId}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "New Name" }
                                """))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /teams/{id} not found → 404 ────────────────────────────────────

    @Test
    void patchTeamNotFoundReturns404() throws Exception {
        UUID teamId = UUID.randomUUID();
        when(updateTeamUseCase.execute(any()))
                .thenThrow(new TeamNotFoundException(teamId));

        mockMvc.perform(patch("/api/v1/teams/{teamId}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "New Name" }
                                """))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /teams/{id}/members/{uid} → 200 ────────────────────────────────

    @Test
    void patchMemberRoleReturns200() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        MemberResult member = new MemberResult(ownerId, TeamRole.OWNER, Instant.now(), null, null, null);
        TeamResult result = new TeamResult(teamId, tenantId, ownerId, "Alpha", null, List.of(member));
        when(updateTeamMemberRoleUseCase.execute(any())).thenReturn(result);

        mockMvc.perform(patch("/api/v1/teams/{teamId}/members/{userId}", teamId, memberId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "role": "VIEWER" }
                                """))
                .andExpect(status().isOk());
    }

    // ── PATCH /teams/{id}/members/{uid} missing role → 400 ───────────────────

    @Test
    void patchMemberRoleMissingRoleReturns400() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/teams/{teamId}/members/{userId}", teamId, memberId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /teams/{id}/members/{uid} non-owner → 403 ─────────────────────

    @Test
    void patchMemberRoleForbiddenReturns403() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(updateTeamMemberRoleUseCase.execute(any()))
                .thenThrow(new UnauthorizedException("Only team owners can change member roles"));

        mockMvc.perform(patch("/api/v1/teams/{teamId}/members/{userId}", teamId, memberId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "role": "VIEWER" }
                                """))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /teams/{id}/members/{uid} not found → 404 ─────────────────────

    @Test
    void patchMemberRoleNotFoundReturns404() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(updateTeamMemberRoleUseCase.execute(any()))
                .thenThrow(new TeamNotFoundException(teamId));

        mockMvc.perform(patch("/api/v1/teams/{teamId}/members/{userId}", teamId, memberId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "role": "VIEWER" }
                                """))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /teams/{id}/members/{uid} self-role-change → 409 ──────────────

    @Test
    void patchMemberRoleSelfChangeReturns409() throws Exception {
        UUID teamId = UUID.randomUUID();
        when(updateTeamMemberRoleUseCase.execute(any()))
                .thenThrow(new SelfRoleChangeException());

        mockMvc.perform(patch("/api/v1/teams/{teamId}/members/{userId}", teamId, ownerId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "role": "MEMBER" }
                                """))
                .andExpect(status().isConflict());
    }

    // ── PATCH /teams/{id}/members/{uid} OWNER role → 400 ─────────────────────

    @Test
    void patchMemberRoleOwnerRoleReturns400() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(updateTeamMemberRoleUseCase.execute(any()))
                .thenThrow(new IllegalArgumentException("Cannot assign OWNER role via changeRole"));

        mockMvc.perform(patch("/api/v1/teams/{teamId}/members/{userId}", teamId, memberId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "role": "OWNER" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request"));
    }

    // ── PATCH /teams/{id} description-only → 200 ─────────────────────────────

    @Test
    void patchTeamDescOnlyReturns200() throws Exception {
        UUID teamId = UUID.randomUUID();
        MemberResult member = new MemberResult(ownerId, TeamRole.OWNER, Instant.now(), null, null, null);
        TeamResult result = new TeamResult(teamId, tenantId, ownerId, "Alpha", "new desc", List.of(member));
        when(updateTeamUseCase.execute(any())).thenReturn(result);

        mockMvc.perform(patch("/api/v1/teams/{teamId}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "new desc" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("new desc"));
    }

    // ── PATCH /teams/{id} name + description → 200 ───────────────────────────

    @Test
    void patchTeamBothFieldsReturns200() throws Exception {
        UUID teamId = UUID.randomUUID();
        MemberResult member = new MemberResult(ownerId, TeamRole.OWNER, Instant.now(), null, null, null);
        TeamResult result = new TeamResult(teamId, tenantId, ownerId, "new", "desc", List.of(member));
        when(updateTeamUseCase.execute(any())).thenReturn(result);

        mockMvc.perform(patch("/api/v1/teams/{teamId}", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "new", "description": "desc" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"))
                .andExpect(jsonPath("$.description").value("desc"));
    }
}
