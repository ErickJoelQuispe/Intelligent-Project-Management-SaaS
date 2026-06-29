package com.epm.user.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.epm.user.domain.exception.ActiveInvitationExistsException;
import com.epm.user.domain.exception.InvitationAlreadyUsedException;
import com.epm.user.domain.exception.InvitationExpiredException;
import com.epm.user.domain.exception.InvitationNotFoundException;
import com.epm.user.domain.model.Invitation;
import com.epm.user.domain.port.in.CreateInvitationUseCase;
import com.epm.user.domain.port.in.MarkInvitationUsedUseCase;
import com.epm.user.domain.port.in.ValidateInvitationUseCase;
import com.epm.user.domain.port.in.result.InvitationResult;
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
 * @WebMvcTest for {@link InvitationController}.
 */
@WebMvcTest(InvitationController.class)
@Import({SecurityConfig.class, JwtClaimsExtractor.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false"
})
class InvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateInvitationUseCase createInvitationUseCase;

    @MockitoBean
    private ValidateInvitationUseCase validateInvitationUseCase;

    @MockitoBean
    private MarkInvitationUsedUseCase markInvitationUsedUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID teamId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID invitationId = UUID.randomUUID();

    // ── POST /api/v1/teams/{teamId}/invite → 201 ─────────────────────────────

    @Test
    void inviteReturns201() throws Exception {
        Instant expiresAt = Instant.now().plus(72, ChronoUnit.HOURS);
        InvitationResult result = new InvitationResult(invitationId, "alice@example.com", expiresAt);

        when(createInvitationUseCase.createInvitation(any(), any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/teams/{teamId}/invite", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(callerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "alice@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationId").value(invitationId.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    // ── POST /invite invalid email → 400 ─────────────────────────────────────

    @Test
    void inviteInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/teams/{teamId}/invite", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(callerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "not-an-email" }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── POST /invite duplicate → 409 ──────────────────────────────────────────

    @Test
    void inviteDuplicateReturns409() throws Exception {
        when(createInvitationUseCase.createInvitation(any(), any()))
                .thenThrow(new ActiveInvitationExistsException("alice@example.com", tenantId));

        mockMvc.perform(post("/api/v1/teams/{teamId}/invite", teamId)
                        .with(jwt().jwt(j -> j
                                .subject(callerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "alice@example.com" }
                                """))
                .andExpect(status().isConflict());
    }

    // ── GET /api/v1/invitations/validate → 200 ────────────────────────────────

    @Test
    void validateReturns200() throws Exception {
        UUID invId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(72, ChronoUnit.HOURS);
        Invitation invitation = Invitation.reconstitute(
                invId, teamId, tenantId, "alice@example.com",
                "hash", "VIEWER", expiresAt, null, "system", Instant.now(), 0L);

        when(validateInvitationUseCase.validateInvitation("valid-token"))
                .thenReturn(invitation);

        mockMvc.perform(get("/api/v1/invitations/validate")
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationId").value(invId.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.teamId").value(teamId.toString()))
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    // ── GET /validate not found → 404 ─────────────────────────────────────────

    @Test
    void validateNotFoundReturns404() throws Exception {
        when(validateInvitationUseCase.validateInvitation("bad-token"))
                .thenThrow(new InvitationNotFoundException("Invitation not found for given token"));

        mockMvc.perform(get("/api/v1/invitations/validate")
                        .param("token", "bad-token"))
                .andExpect(status().isNotFound());
    }

    // ── GET /validate expired → 410 ───────────────────────────────────────────

    @Test
    void validateExpiredReturns410() throws Exception {
        when(validateInvitationUseCase.validateInvitation("expired-token"))
                .thenThrow(new InvitationExpiredException(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/invitations/validate")
                        .param("token", "expired-token"))
                .andExpect(status().isGone());
    }

    // ── GET /validate already used → 409 ─────────────────────────────────────

    @Test
    void validateAlreadyUsedReturns409() throws Exception {
        when(validateInvitationUseCase.validateInvitation("used-token"))
                .thenThrow(new InvitationAlreadyUsedException(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/invitations/validate")
                        .param("token", "used-token"))
                .andExpect(status().isConflict());
    }

    // ── POST /api/v1/invitations/{id}/use → 204 ───────────────────────────────

    @Test
    void markUsedReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/invitations/{invitationId}/use", invitationId))
                .andExpect(status().isNoContent());
    }

    // ── POST /use not found → 404 ─────────────────────────────────────────────

    @Test
    void markUsedNotFoundReturns404() throws Exception {
        doThrow(new InvitationNotFoundException("Invitation " + invitationId + " not found"))
                .when(markInvitationUsedUseCase).markInvitationUsed(invitationId);

        mockMvc.perform(post("/api/v1/invitations/{invitationId}/use", invitationId))
                .andExpect(status().isNotFound());
    }

    // ── POST /use already used → 409 ─────────────────────────────────────────

    @Test
    void markUsedAlreadyUsedReturns409() throws Exception {
        doThrow(new InvitationAlreadyUsedException(invitationId))
                .when(markInvitationUsedUseCase).markInvitationUsed(invitationId);

        mockMvc.perform(post("/api/v1/invitations/{invitationId}/use", invitationId))
                .andExpect(status().isConflict());
    }
}
