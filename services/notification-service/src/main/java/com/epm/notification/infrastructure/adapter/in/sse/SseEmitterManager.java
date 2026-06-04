package com.epm.notification.infrastructure.adapter.in.sse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.epm.notification.domain.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Manages SSE emitters per recipient user.
 *
 * <p>Each user can have multiple emitters (one per browser tab).
 * On notification creation, all emitters for that user receive the event.
 * Emitters that complete, time out, or error are automatically removed.
 */
@Component
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final ConcurrentHashMap<UUID, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    /**
     * Creates and registers a new SSE emitter for the given user.
     *
     * @param recipientUserId the user to subscribe
     * @return a new SseEmitter (the caller sends it as the SSE response)
     */
    public SseEmitter subscribe(UUID recipientUserId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emittersByUser.computeIfAbsent(recipientUserId, _k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Send an initial comment to confirm the connection is alive
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            log.warn("Failed to send initial comment to user {}: {}", recipientUserId, e.getMessage());
            removeEmitter(recipientUserId, emitter);
            return emitter;
        }

        emitter.onCompletion(() -> removeEmitter(recipientUserId, emitter));
        emitter.onTimeout(() -> removeEmitter(recipientUserId, emitter));
        emitter.onError(_e -> removeEmitter(recipientUserId, emitter));

        log.debug("SSE subscriber added for user {} (total: {})", recipientUserId,
                emittersByUser.get(recipientUserId).size());
        return emitter;
    }

    /**
     * Emits a notification event to ALL emitters registered for the recipient user.
     *
     * @param notification the notification to broadcast via SSE
     */
    public void emitNotification(Notification notification) {
        UUID userId = notification.getRecipientUserId();
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        log.debug("Emitting notification {} to user {} ({} emitters)",
                notification.getId(), userId, emitters.size());

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
            } catch (IOException e) {
                log.warn("Failed to emit to user {}: {}", userId, e.getMessage());
                removeEmitter(userId, emitter);
            }
        }
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByUser.remove(userId);
            }
        }
    }
}
