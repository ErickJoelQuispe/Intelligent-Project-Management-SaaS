package com.epm.notification.infrastructure.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for notification endpoints.
 *
 * <p>All endpoints require an authenticated JWT. The authenticated user's
 * {@code sub} claim is used as the userId, and {@code tenant_id} as the tenantId.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationApplicationService notificationService;

    public NotificationController(NotificationApplicationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * GET /api/v1/notifications — list all notifications for the authenticated user.
     */
    @GetMapping
    public List<NotificationResponse> listNotifications(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = extractTenantId(jwt);
        List<Notification> notifications = notificationService.listForUser(tenantId, userId);
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
