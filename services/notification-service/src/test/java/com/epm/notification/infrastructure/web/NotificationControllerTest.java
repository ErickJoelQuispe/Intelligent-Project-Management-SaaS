package com.epm.notification.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.exception.NotificationAccessDeniedException;
import com.epm.notification.domain.exception.NotificationNotFoundException;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.infrastructure.adapter.in.web.GlobalExceptionHandler;
import com.epm.notification.infrastructure.adapter.in.web.NotificationController;
import com.epm.notification.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @WebMvcTest for NotificationController (T-C-06).
 *
 * <p>Tests authentication, endpoint behavior, pagination constraints (H5 fix), and
 * DataIntegrityViolationException → 409 mapping (H6 fix) with mocked use cases.
 */
@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false"
})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationApplicationService notificationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    // ── GET /api/v1/notifications — list for authenticated user ──────────────

    @Test
    void listNotifications_withValidJwt_returnsNotifications() throws Exception {
        UUID refId = UUID.randomUUID();
        Notification notification = Notification.reconstitute(
                UUID.randomUUID(), tenantId, userId,
                NotificationType.TASK_ASSIGNED, refId, "Task assigned to you",
                false, Instant.now());

        when(notificationService.listForUserPaged(eq(tenantId), eq(userId), anyInt(), anyInt()))
                .thenReturn(List.of(notification));

        mockMvc.perform(get("/api/v1/notifications")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].message").value("Task assigned to you"));
    }

    @Test
    void listNotifications_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/notifications/unread-count ────────────────────────────────

    @Test
    void unreadCount_returnsCountForUser() throws Exception {
        when(notificationService.countUnread(tenantId, userId)).thenReturn(3);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void unreadCount_whenZero_returnsZero() throws Exception {
        when(notificationService.countUnread(tenantId, userId)).thenReturn(0);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    // ── PATCH /api/v1/notifications/{id}/read ────────────────────────────────

    @Test
    void markAsRead_withValidOwner_returns204() throws Exception {
        UUID notifId = UUID.randomUUID();
        doNothing().when(notificationService).markAsRead(notifId, userId);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notifId)
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNoContent());
    }

    @Test
    void markAsRead_whenNotFound_returns404() throws Exception {
        UUID notifId = UUID.randomUUID();
        doThrow(new NotificationNotFoundException(notifId))
                .when(notificationService).markAsRead(notifId, userId);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notifId)
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAsRead_whenNotOwner_returns403() throws Exception {
        UUID notifId = UUID.randomUUID();
        doThrow(new NotificationAccessDeniedException(notifId, userId))
                .when(notificationService).markAsRead(notifId, userId);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notifId)
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/v1/notifications/mark-all-read ─────────────────────────────

    @Test
    void markAllRead_withValidJwt_returns204() throws Exception {
        doNothing().when(notificationService).markAllAsRead(any(), any());

        mockMvc.perform(post("/api/v1/notifications/mark-all-read")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isNoContent());
    }

    @Test
    void markAllRead_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/mark-all-read"))
                .andExpect(status().isUnauthorized());
    }

    // ── H5: pagination — page/size validation ────────────────────────────────
    //
    // The listNotifications endpoint must accept page/size params and reject invalid values.
    // RED: before adding @RequestParam + @Validated constraints, these return 200 (no param)
    //      or pass bad values through — the 400 assertions FAIL.
    // GREEN: after adding @Min/@Max-annotated params with @Validated, Spring returns 400.

    @Test
    void listNotifications_withValidPageAndSize_returns200() throws Exception {
        when(notificationService.listForUserPaged(eq(tenantId), eq(userId), anyInt(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "0")
                        .param("size", "20")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void listNotifications_withNegativePage_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "-1")
                        .param("size", "20")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listNotifications_withSizeAbove100_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "0")
                        .param("size", "101")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listNotifications_withSizeZero_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "0")
                        .param("size", "0")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isBadRequest());
    }

    // ── H6: DataIntegrityViolationException → 409 Conflict ───────────────────
    //
    // RED: before adding the @ExceptionHandler in GlobalExceptionHandler, the generic
    //      Exception handler returns 500 — the isConflict() assertion FAILS.
    // GREEN: after adding the handler, 409 is returned.

    @Test
    void listNotifications_whenServiceThrowsDataIntegrityViolation_returns409() throws Exception {
        when(notificationService.listForUserPaged(eq(tenantId), eq(userId), anyInt(), anyInt()))
                .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "0")
                        .param("size", "20")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isConflict());
    }
}
