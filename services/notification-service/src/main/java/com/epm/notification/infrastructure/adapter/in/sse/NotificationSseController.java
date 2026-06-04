package com.epm.notification.infrastructure.adapter.in.sse;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST controller providing a Server-Sent Events (SSE) stream for real-time
 * notification delivery.
 *
 * <p>Each authenticated user gets a dedicated stream. When a new notification
 * is created, the {@link SseEmitterManager} pushes it to all open connections
 * for the recipient user.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationSseController {

    private final SseEmitterManager sseEmitterManager;

    public NotificationSseController(SseEmitterManager sseEmitterManager) {
        this.sseEmitterManager = sseEmitterManager;
    }

    /**
     * Opens an SSE stream for the authenticated user.
     * <p>The connection stays open. Events are pushed as they arrive.
     * Use {@code EventSource} on the frontend to consume this stream.
     *
     * @param jwt the authenticated user's JWT
     * @return a never-ending SseEmitter
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return sseEmitterManager.subscribe(userId);
    }
}
