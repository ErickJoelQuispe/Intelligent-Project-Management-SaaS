package com.epm.user.infrastructure.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.port.in.GetOwnProfileUseCase;
import com.epm.user.domain.port.in.ListTenantUsersUseCase;
import com.epm.user.domain.port.in.UpdateOwnProfileUseCase;
import com.epm.user.domain.port.in.command.UpdateProfileCommand;
import com.epm.user.domain.port.in.dto.JwtClaimsDto;
import com.epm.user.domain.port.in.result.UserProfileResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for user profile operations.
 *
 * <p>GET /me returns X-Profile-Source: database | provisional.
 * <p>GET / lists all active tenant users (capped at 100).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetOwnProfileUseCase getOwnProfileUseCase;
    private final UpdateOwnProfileUseCase updateOwnProfileUseCase;
    private final ListTenantUsersUseCase listTenantUsersUseCase;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public UserController(GetOwnProfileUseCase getOwnProfileUseCase,
            UpdateOwnProfileUseCase updateOwnProfileUseCase,
            ListTenantUsersUseCase listTenantUsersUseCase,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.getOwnProfileUseCase = getOwnProfileUseCase;
        this.updateOwnProfileUseCase = updateOwnProfileUseCase;
        this.listTenantUsersUseCase = listTenantUsersUseCase;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    /**
     * Returns the authenticated user's profile.
     * Sets {@code X-Profile-Source: database} or {@code X-Profile-Source: provisional}.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getOwnProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        JwtClaimsDto claims = jwtClaimsExtractor.toClaims(jwt);

        UserProfileResult result = getOwnProfileUseCase.getProfile(userId, tenantId, claims);
        String profileSource = result.provisional() ? "provisional" : "database";

        return ResponseEntity.ok()
                .header("X-Profile-Source", profileSource)
                .body(toResponse(result));
    }

    /**
     * Updates the authenticated user's profile.
     */
    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateOwnProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);

        UpdateProfileCommand command = new UpdateProfileCommand(
                request.firstName(), request.lastName(), request.bio(),
                request.avatarUrl(), request.version());
        UserProfileResult result = updateOwnProfileUseCase.updateProfile(userId, tenantId, command);

        return ResponseEntity.ok()
                .header("X-Profile-Source", "database")
                .body(toResponse(result));
    }

    /**
     * Lists all active users for the calling user's tenant.
     * Results are capped at 100 — TODO: add cursor-based pagination.
     */
    @GetMapping
    public List<TenantUserResponse> listTenantUsers(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return listTenantUsersUseCase.listTenantUsers(tenantId).stream()
                .map(u -> new TenantUserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName()))
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UserProfileResponse toResponse(UserProfileResult result) {
        return new UserProfileResponse(result.id(), result.tenantId(), result.email(),
                result.firstName(), result.lastName(), result.bio(), result.avatarUrl(),
                result.version());
    }
}
