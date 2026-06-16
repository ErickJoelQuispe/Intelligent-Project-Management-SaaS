package com.epm.notification.infrastructure.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.epm.notification.domain.port.out.NotificationPushPort;
import com.epm.notification.infrastructure.AbstractPostgresIT;
import com.epm.notification.infrastructure.adapter.in.ws.WebSocketChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Integration test for WebSocket/STOMP endpoint (T-WS-03).
 *
 * <p>Tests that:
 * - The wired {@link WebSocketChannelInterceptor} REJECTS a no-token CONNECT
 *   ({@link AccessDeniedException}) — a real regression detector (FIX E).
 * - An end-to-end no-token STOMP CONNECT does NOT establish a usable session.
 * - {@link NotificationPushPort} is wired correctly.
 *
 * <p>Uses mocked JwtDecoder to avoid Keycloak dependency in CI.
 * Uses @SpringBootTest RANDOM_PORT for real WebSocket server.
 * DB is provided by Testcontainers via AbstractPostgresIT (@ServiceConnection).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.mail.host=localhost",
    "spring.mail.port=1025",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class WebSocketIntegrationTest extends AbstractPostgresIT {

    @LocalServerPort
    private int port;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private NotificationPushPort notificationPushPort;

    @Autowired
    private WebSocketChannelInterceptor webSocketChannelInterceptor;

    // ── FIX E: real regression detector for no-token CONNECT rejection ────────
    //
    // The previous version of this test caught ALL exceptions and only asserted a bean was
    // non-null — it passed even if the CONNECT validation was removed (a no-op). This rewrite
    // drives the WIRED interceptor's preSend with a no-token CONNECT and asserts it throws
    // AccessDeniedException. It is genuinely RED if handleConnect's missing-token check is removed:
    // preSend would return the message normally and assertThatThrownBy would FAIL.

    @Test
    void preSend_noTokenConnect_throughWiredInterceptor_throwsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(Map.of()); // no "token" attribute
        accessor.setLeaveMutable(true);
        Message<?> connect = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageChannel anyChannel = (message, timeout) -> true;

        assertThatThrownBy(() -> webSocketChannelInterceptor.preSend(connect, anyChannel))
                .as("A no-token STOMP CONNECT must be rejected by the wired interceptor")
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Missing token");
    }

    // ── FIX E: end-to-end — a no-token CONNECT never yields a usable session ──
    //
    // At STOMP level the CONNECT is rejected, so afterConnected must NOT fire. We assert the
    // session future never completes successfully within the timeout. This complements the
    // interceptor-level detector with a real transport-level check.

    @Test
    void connect_withNoToken_doesNotEstablishUsableSession() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        CompletableFuture<StompSession> connectedFuture = new CompletableFuture<>();

        String wsUrl = "ws://localhost:" + port + "/ws/notifications";

        stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connectedFuture.complete(session);
            }
        });

        // afterConnected must NEVER fire for a no-token CONNECT → the future stays incomplete.
        assertThatThrownBy(() -> connectedFuture.get(3, TimeUnit.SECONDS))
                .as("No-token CONNECT must NOT produce a STOMP-connected session")
                .isInstanceOf(TimeoutException.class);
    }

    // ── T-WS-03b: NotificationPushPort bean is available in context ───────

    @Test
    void applicationContext_notificationPushPort_isWiredCorrectly() {
        assertThat(notificationPushPort).isNotNull();
        assertThat(notificationPushPort)
                .isInstanceOf(com.epm.notification.infrastructure.adapter.out.ws.StompNotificationPushAdapter.class);
    }
}
