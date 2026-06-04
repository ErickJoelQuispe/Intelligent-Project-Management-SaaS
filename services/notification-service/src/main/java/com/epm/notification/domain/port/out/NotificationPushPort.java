package com.epm.notification.domain.port.out;

import java.util.UUID;

/**
 * Output port for pushing real-time notifications to connected clients.
 *
 * <p>Implemented by the WebSocket/STOMP adapter in the infrastructure layer.
 * Fire-and-forget: no buffering, no acknowledgement required.
 * Pure Java — no Spring, no infrastructure dependencies.
 */
public interface NotificationPushPort {

    /**
     * Pushes a notification payload to a specific user.
     *
     * @param userId  the recipient user ID
     * @param payload the notification payload to push (serialized by the adapter)
     */
    void pushToUser(UUID userId, Object payload);
}
