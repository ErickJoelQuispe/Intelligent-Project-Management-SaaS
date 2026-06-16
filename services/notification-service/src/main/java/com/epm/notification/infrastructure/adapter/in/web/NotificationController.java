package com.epm.notification.infrastructure.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for notification endpoints.
 *
 * <p>All endpoints require an authenticated JWT. The authenticated user's
 * {@code sub} claim is used as the userId, and {@code tenant_id} as the tenantId.
 *
 * <p>{@link Validated} is required so that {@code @Min}/{@code @Max} constraints on
 * {@code @RequestParam} are enforced by Spring (Bean Validation on method parameters).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Validated
public class NotificationController {

    private final NotificationApplicationService notificationService;

    public NotificationController(NotificationApplicationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * GET /api/v1/notifications — list notifications for the authenticated user with pagination.
     *
     * <p>Pagination is enforced with a hard clamp: {@code page >= 0}, {@code 1 <= size <= 100}.
     * Requests outside these bounds return {@code 400 Bad Request} (H5 fix — DoS guard).
     *
     * @param page zero-based page index (default 0, must be >= 0)
     * @param size page size (default 20, must be between 1 and 100)
     */
    @GetMapping
    public List<NotificationResponse> listNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = extractTenantId(jwt);
        List<Notification> notifications = notificationService.listForUserPaged(tenantId, userId, page, size);
        return notifications.stream().map(NotificationResponse::from).toList();
    }

    /**
     * GET /api/v1/notifications/unread-count — returns the unread count for the authenticated user.
     */
    @GetMapping("/unread-count")
    public Map<String, Integer> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = extractTenantId(jwt);
        int count = notificationService.countUnread(tenantId, userId);
        return Map.of("count", count);
    }

    /**
     * PATCH /api/v1/notifications/{id}/read — mark a single notification as read.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        notificationService.markAsRead(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/notifications/mark-all-read — mark all notifications as read.
     */
    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = extractTenantId(jwt);
        notificationService.markAllAsRead(tenantId, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID extractTenantId(Jwt jwt) {
        String tenantIdStr = jwt.getClaimAsString("tenant_id");
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            throw new IllegalArgumentException("JWT is missing tenant_id claim");
        }
        return UUID.fromString(tenantIdStr);
    }
}
