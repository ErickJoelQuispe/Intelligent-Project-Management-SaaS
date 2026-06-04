package com.epm.notification.infrastructure.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.in.GetPreferencesUseCase;
import com.epm.notification.domain.port.in.UpdatePreferenceUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for notification preference endpoints.
 *
 * <p>All endpoints require an authenticated JWT. The authenticated user's
 * {@code sub} claim is used as userId and {@code tenant_id} as tenantId.
 *
 * <p>GET  /api/v1/notifications/preferences → returns full preference matrix
 * PUT  /api/v1/notifications/preferences/{eventType}/{channel} → upsert preference
 */
@RestController
@RequestMapping("/api/v1/notifications/preferences")
public class NotificationPreferencesController {

    private final GetPreferencesUseCase getPreferencesUseCase;
    private final UpdatePreferenceUseCase updatePreferenceUseCase;

    public NotificationPreferencesController(GetPreferencesUseCase getPreferencesUseCase,
            UpdatePreferenceUseCase updatePreferenceUseCase) {
        this.getPreferencesUseCase = getPreferencesUseCase;
        this.updatePreferenceUseCase = updatePreferenceUseCase;
    }

    /**
     * GET /api/v1/notifications/preferences — return all preferences for the authenticated user.
     */
    @GetMapping
    public List<PreferenceResponse> getPreferences(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = extractTenantId(jwt);
        return getPreferencesUseCase.getPreferences(userId, tenantId).stream()
                .map(PreferenceResponse::from)
                .toList();
    }

    /**
     * PUT /api/v1/notifications/preferences/{eventType}/{channel}
     * Body: {@code {"enabled": boolean}}
     */
    @PutMapping("/{eventType}/{channel}")
    public ResponseEntity<Void> updatePreference(
            @PathVariable String eventType,
            @PathVariable String channel,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal Jwt jwt) {

        NotificationType type;
        try {
            type = NotificationType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        NotificationChannel ch;
        try {
            ch = NotificationChannel.valueOf(channel);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = extractTenantId(jwt);

        updatePreferenceUseCase.updatePreference(userId, tenantId, type, ch, enabled);

        return ResponseEntity.ok().build();
    }

    private UUID extractTenantId(Jwt jwt) {
        String tenantIdStr = jwt.getClaimAsString("tenant_id");
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            throw new IllegalArgumentException("JWT is missing tenant_id claim");
        }
        return UUID.fromString(tenantIdStr);
    }
}
