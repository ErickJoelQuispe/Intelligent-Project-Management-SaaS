package com.epm.user.infrastructure.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.exception.InvalidPreferencesException;
import com.epm.user.domain.exception.ProfileNotFoundException;
import com.epm.user.domain.port.in.DeleteOwnProfileUseCase;
import com.epm.user.domain.port.in.GetOwnProfileUseCase;
import com.epm.user.domain.port.in.ListTenantUsersUseCase;
import com.epm.user.domain.port.in.UpdateOwnProfileUseCase;
import com.epm.user.domain.port.in.command.UpdateProfileCommand;
import com.epm.user.domain.port.in.dto.JwtClaimsDto;
import com.epm.user.domain.port.in.result.UserProfileResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for user profile operations.
 *
 * <p>GET /me returns X-Profile-Source: database | provisional.
 * <p>GET / lists a page of active tenant users (default page=0, size=100, max size=100).
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final GetOwnProfileUseCase getOwnProfileUseCase;
    private final UpdateOwnProfileUseCase updateOwnProfileUseCase;
    private final ListTenantUsersUseCase listTenantUsersUseCase;
    private final DeleteOwnProfileUseCase deleteOwnProfileUseCase;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public UserController(GetOwnProfileUseCase getOwnProfileUseCase,
            UpdateOwnProfileUseCase updateOwnProfileUseCase,
            ListTenantUsersUseCase listTenantUsersUseCase,
            DeleteOwnProfileUseCase deleteOwnProfileUseCase,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.getOwnProfileUseCase = getOwnProfileUseCase;
        this.updateOwnProfileUseCase = updateOwnProfileUseCase;
        this.listTenantUsersUseCase = listTenantUsersUseCase;
        this.deleteOwnProfileUseCase = deleteOwnProfileUseCase;
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
                request.avatarUrl(), request.version(), request.preferences());
        UserProfileResult result = updateOwnProfileUseCase.updateProfile(userId, tenantId, command);

        return ResponseEntity.ok()
                .header("X-Profile-Source", "database")
                .body(toResponse(result));
    }

    /**
     * Lists active users for the calling user's tenant with offset pagination.
     * Accepts optional {@code page} (default 0) and {@code size} (default 100, max 100).
     */
    @GetMapping
    public List<TenantUserResponse> listTenantUsers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) int size) {
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return listTenantUsersUseCase.listTenantUsers(tenantId, page, size).stream()
                .map(u -> new TenantUserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName()))
                .toList();
    }

    /**
     * Soft-deletes the authenticated user's own profile.
     *
     * <p>Step 2 of the frontend-orchestrated account deletion flow.
     * On success returns 204. If the profile does not exist, returns 404.
     *
     * @param jwt the authenticated JWT principal
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOwnProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = jwtClaimsExtractor.getUserId(jwt);
        deleteOwnProfileUseCase.execute(userId);
    }

    /**
     * Handles invalid workspace preferences — returns 400 with field error detail.
     */
    @ExceptionHandler(InvalidPreferencesException.class)
    public ResponseEntity<PreferencesErrorResponse> handleInvalidPreferences(InvalidPreferencesException ex) {
        return ResponseEntity.badRequest()
                .body(new PreferencesErrorResponse("preferences." + extractField(ex), ex.getMessage()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UserProfileResponse toResponse(UserProfileResult result) {
        return new UserProfileResponse(result.id(), result.tenantId(), result.email(),
                result.firstName(), result.lastName(), result.bio(), result.avatarUrl(),
                result.version(), result.preferences());
    }

    private String extractField(InvalidPreferencesException ex) {
        // Exception message format: "Invalid value for field 'fieldName': value"
        String msg = ex.getMessage();
        if (msg == null) return "unknown";
        int start = msg.indexOf('\'');
        int end = msg.indexOf('\'', start + 1);
        if (start >= 0 && end > start) {
            return msg.substring(start + 1, end);
        }
        return "unknown";
    }

    /**
     * Simple error response for invalid preferences.
     */
    public record PreferencesErrorResponse(String field, String message) {}
}
