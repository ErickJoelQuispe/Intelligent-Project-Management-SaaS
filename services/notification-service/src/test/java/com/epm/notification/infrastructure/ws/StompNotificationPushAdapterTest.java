package com.epm.notification.infrastructure.ws;

import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;

import com.epm.notification.infrastructure.adapter.out.ws.StompNotificationPushAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Unit tests for StompNotificationPushAdapter (T-WS-02).
 *
 * <p>Verifies that pushToUser calls SimpMessagingTemplate with the correct
 * destination format: /topic/notifications/{userId}
 */
@ExtendWith(MockitoExtension.class)
class StompNotificationPushAdapterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private StompNotificationPushAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StompNotificationPushAdapter(messagingTemplate);
    }

    // ── T-WS-02a: pushToUser sends to /topic/notifications/{userId} ───────

    @Test
    void pushToUser_sendsToCorrectDestination() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Object payload = Map.of("message", "Task assigned");

        adapter.pushToUser(userId, payload);

        verify(messagingTemplate).convertAndSend(
                "/topic/notifications/22222222-2222-2222-2222-222222222222",
                (Object) payload);
    }

    // ── T-WS-02b: pushToUser uses userId.toString() correctly ─────────────

    @Test
    void pushToUser_withDifferentUserId_usesCorrectDestination() {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Object payload = "notification payload";

        adapter.pushToUser(userId, payload);

        verify(messagingTemplate).convertAndSend(
                "/topic/notifications/33333333-3333-3333-3333-333333333333",
                (Object) payload);
    }
}
