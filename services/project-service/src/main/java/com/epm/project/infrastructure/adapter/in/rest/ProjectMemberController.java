package com.epm.project.infrastructure.adapter.in.rest;

import java.util.UUID;

import com.epm.project.domain.port.in.CheckProjectMembershipUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for project membership checks.
 *
 * <p>Exposes {@code GET /api/v1/projects/{id}/members/{userId}} to allow
 * task-service's Feign client to verify project membership before task creation.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectMemberController {

    private final CheckProjectMembershipUseCase checkProjectMembershipUseCase;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public ProjectMemberController(
            CheckProjectMembershipUseCase checkProjectMembershipUseCase,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.checkProjectMembershipUseCase = checkProjectMembershipUseCase;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    /**
     * GET /api/v1/projects/{id}/members/{userId}
     *
     * <p>Returns 200 with a {@link MembershipResponse} when the user is an active member,
     * or 404 when the user is not a member of the project.
     *
     * @param jwt      the authenticated caller's JWT token
     * @param id       the project UUID
     * @param userId   the user UUID to check for membership
     * @return 200 with membership body, or 404 if not a member
     */
    @GetMapping("/{id}/members/{userId}")
    public ResponseEntity<MembershipResponse> checkMembership(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        boolean isMember = checkProjectMembershipUseCase.isMember(id, userId, tenantId);
        if (!isMember) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new MembershipResponse(id, userId, true));
    }
}
