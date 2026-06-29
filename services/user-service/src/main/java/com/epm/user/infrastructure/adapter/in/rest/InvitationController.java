package com.epm.user.infrastructure.adapter.in.rest;

import java.util.UUID;

import com.epm.user.domain.model.Invitation;
import com.epm.user.domain.port.in.CreateInvitationUseCase;
import com.epm.user.domain.port.in.MarkInvitationUsedUseCase;
import com.epm.user.domain.port.in.ValidateInvitationUseCase;
import com.epm.user.domain.port.in.command.CreateInvitationCommand;
import com.epm.user.domain.port.in.result.InvitationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for invitation operations.
 *
 * <p>{@code POST /api/v1/teams/{teamId}/invite} — protected, requires authenticated caller.
 * {@code GET /api/v1/invitations/validate} — internal only (no auth; not exposed externally by gateway).
 * {@code POST /api/v1/invitations/{invitationId}/use} — internal only.
 */
@RestController
public class InvitationController {

    private final CreateInvitationUseCase createInvitationUseCase;
    private final ValidateInvitationUseCase validateInvitationUseCase;
    private final MarkInvitationUsedUseCase markInvitationUsedUseCase;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public InvitationController(CreateInvitationUseCase createInvitationUseCase,
            ValidateInvitationUseCase validateInvitationUseCase,
            MarkInvitationUsedUseCase markInvitationUsedUseCase,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.createInvitationUseCase = createInvitationUseCase;
        this.validateInvitationUseCase = validateInvitationUseCase;
        this.markInvitationUsedUseCase = markInvitationUsedUseCase;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    /**
     * POST /api/v1/teams/{teamId}/invite → 201
     *
     * <p>Authenticated endpoint — caller must be an ADMIN of the team.
     * Authorization enforcement is expected at the gateway or service-mesh layer;
     * the controller trusts the JWT claims present in the request.
     */
    @PostMapping("/api/v1/teams/{teamId}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID teamId,
            @Valid @RequestBody InviteRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);

        CreateInvitationCommand command = new CreateInvitationCommand(
                teamId, tenantId, request.email(), callerId.toString());

        InvitationResult result = createInvitationUseCase.createInvitation(callerId, command);
        return new InvitationResponse(result.invitationId(), result.email(), result.expiresAt());
    }

    /**
     * GET /api/v1/invitations/validate?token={token} → 200
     *
     * <p>Internal endpoint — called by auth-service. No auth annotation because the
     * API gateway does NOT expose this route externally.
     */
    @GetMapping("/api/v1/invitations/validate")
    public ValidateInvitationResponse validateInvitation(
            @RequestParam @NotBlank String token) {
        Invitation invitation = validateInvitationUseCase.validateInvitation(token);
        return new ValidateInvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getTenantId(),
                invitation.getTeamId(),
                invitation.getRole());
    }

    /**
     * POST /api/v1/invitations/{invitationId}/use → 204
     *
     * <p>Internal endpoint — called by auth-service after successful account creation.
     * No auth annotation because the API gateway does NOT expose this route externally.
     */
    @PostMapping("/api/v1/invitations/{invitationId}/use")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markInvitationUsed(@PathVariable UUID invitationId) {
        markInvitationUsedUseCase.markInvitationUsed(invitationId);
    }
}
