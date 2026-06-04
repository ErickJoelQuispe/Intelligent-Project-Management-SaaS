package com.epm.notification.infrastructure.adapter.out.ws;

import java.util.UUID;

import com.epm.notification.domain.port.out.NotificationPushPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * STOMP adapter implementing {@link NotificationPushPort}.
 *
 * <p>Pushes notification payloads to per-user STOMP topics using
 * {@link SimpMessagingTemplate}. Destination pattern:
 * {@code /topic/notifications/{userId}}
 *
 * <p>Fire-and-forget: if the user is not connected, the message is silently
 * dropped by the simple broker (no buffering).
 */
public class StompNotificationPushAdapter implements NotificationPushPort {

    private static final Logger log = LoggerFactory.getLogger(StompNotificationPushAdapter.class);
    private static final String DESTINATION_PREFIX = "/topic/notifications/";

    private final SimpMessagingTemplate messagingTemplate;

    public StompNotificationPushAdapter(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void pushToUser(UUID userId, Object payload) {
        String destination = DESTINATION_PREFIX + userId.toString();
        log.debug("Pushing notification to STOMP destination: {}", destination);
        messagingTemplate.convertAndSend(destination, payload);
    }
}
