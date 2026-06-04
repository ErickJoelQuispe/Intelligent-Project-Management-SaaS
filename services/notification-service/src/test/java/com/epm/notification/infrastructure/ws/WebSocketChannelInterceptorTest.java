package com.epm.notification.infrastructure.ws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import com.epm.notification.infrastructure.adapter.in.ws.WebSocketChannelInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Unit tests for WebSocketChannelInterceptor (T-WS-01).
 *
 * <p>Verifies JWT validation on STOMP CONNECT frames:
 * - Valid token → accepted, SecurityContext populated
 * - Missing token → AccessDeniedException
 * - Invalid/expired token → AccessDeniedException
 * - Non-CONNECT frames → pass through unchanged
 */
@ExtendWith(MockitoExtension.class)
class WebSocketChannelInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private MessageChannel channel;

    private WebSocketChannelInterceptor interceptor;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String VALID_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketChannelInterceptor(jwtDecoder);
    }

    // ── T-WS-01a: CONNECT with valid token → accepted ───────────────────

    @Test
    void preSend_connectFrameWithValidToken_returnsMessageAndPopulatesSecurityContext() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(USER_ID.toString());
        when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(jwt);

        Message<?> connectMessage = buildConnectMessageWithToken(VALID_TOKEN);

        Message<?> result = interceptor.preSend(connectMessage, channel);

        assertThat(result).isNotNull();
        verify(jwtDecoder).decode(VALID_TOKEN);
    }

    // ── T-WS-01b: CONNECT with no token → AccessDeniedException ─────────

    @Test
    void preSend_connectFrameWithNoToken_throwsAccessDeniedException() {
        Message<?> connectMessage = buildConnectMessageWithNoToken();

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Missing token");

        verify(jwtDecoder, never()).decode(anyString());
    }

    // ── T-WS-01c: CONNECT with invalid/expired token → AccessDeniedException

    @Test
    void preSend_connectFrameWithInvalidToken_throwsAccessDeniedException() {
        when(jwtDecoder.decode("invalid.token")).thenThrow(new JwtException("Token expired"));

        Message<?> connectMessage = buildConnectMessageWithToken("invalid.token");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid token");
    }

    // ── T-WS-01d: Non-CONNECT frames → pass through without validation ───

    @Test
    void preSend_subscribeFrame_passesThrough_noJwtValidation() {
        Message<?> subscribeMessage = buildNonConnectMessage(StompCommand.SUBSCRIBE);

        Message<?> result = interceptor.preSend(subscribeMessage, channel);

        assertThat(result).isSameAs(subscribeMessage);
        verify(jwtDecoder, never()).decode(anyString());
    }

    @Test
    void preSend_sendFrame_passesThrough_noJwtValidation() {
        Message<?> sendMessage = buildNonConnectMessage(StompCommand.SEND);

        Message<?> result = interceptor.preSend(sendMessage, channel);

        assertThat(result).isSameAs(sendMessage);
        verify(jwtDecoder, never()).decode(anyString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Message<?> buildConnectMessageWithToken(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(Map.of("token", token));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> buildConnectMessageWithNoToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(Map.of());
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> buildNonConnectMessage(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // Import assertThat from AssertJ (static)
    private static <T> org.assertj.core.api.AbstractObjectAssert<?, T> assertThat(T actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}
