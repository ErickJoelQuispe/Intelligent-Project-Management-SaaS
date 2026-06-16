package com.epm.notification.infrastructure.ws;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Unit tests for WebSocketChannelInterceptor (T-WS-01).
 *
 * <p>Verifies JWT validation on STOMP CONNECT frames and SUBSCRIBE destination
 * authorization (C2 — topic-hijacking fix):
 * <ul>
 *   <li>Valid CONNECT token → accepted, SecurityContext populated</li>
 *   <li>CONNECT with missing token → AccessDeniedException</li>
 *   <li>CONNECT with invalid/expired token → AccessDeniedException</li>
 *   <li>SUBSCRIBE to own destination → allowed (returns message)</li>
 *   <li>SUBSCRIBE to another user's destination → AccessDeniedException (C2 fix)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WebSocketChannelInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private MessageChannel channel;

    private WebSocketChannelInterceptor interceptor;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
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

    // ── FIX C: CONNECT must NOT leak into SecurityContextHolder ───────────────
    //
    // STOMP frames run on a reused thread pool. If the interceptor populates the
    // thread-local SecurityContextHolder and never clears it, a later frame from a
    // DIFFERENT session on the same thread inherits the previous user's SecurityContext.
    // Principal propagation is done correctly via accessor.setUser(...). The interceptor
    // must therefore NEVER touch SecurityContextHolder.
    //
    // RED while handleConnect calls SecurityContextHolder.getContext().setAuthentication(...):
    //   after preSend the context holds the JwtAuthenticationToken → assertion isNull() FAILS.
    // GREEN after the leaking line is removed: the context stays empty.

    @Test
    void preSend_connectFrameWithValidToken_setsPrincipalOnAccessor_andDoesNotTouchSecurityContextHolder() {
        SecurityContextHolder.clearContext();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(USER_ID.toString());
        when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(jwt);

        Message<?> connectMessage = buildConnectMessageWithToken(VALID_TOKEN);

        Message<?> result = interceptor.preSend(connectMessage, channel);

        // Principal is propagated the CORRECT way — on the STOMP accessor / session.
        StompHeaderAccessor accessor = org.springframework.messaging.support.MessageHeaderAccessor
                .getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser())
                .as("CONNECT must set the principal on the accessor (accessor.getUser())")
                .isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(USER_ID.toString());

        // The thread-local SecurityContextHolder must remain EMPTY — no cross-frame leak.
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("CONNECT must NOT populate the thread-local SecurityContextHolder — STOMP threads "
                        + "are pooled and never cleared, so a leaked context bleeds into another user's frame")
                .isNull();

        SecurityContextHolder.clearContext();
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

    // ── T-WS-01d: SEND frame to a non-broker destination → pass through ───────

    @Test
    void preSend_sendFrameToAppDestination_passesThrough_noJwtValidation() {
        Message<?> sendMessage = buildSendMessage("/app/ack", USER_ID);

        Message<?> result = interceptor.preSend(sendMessage, channel);

        assertThat(result).isSameAs(sendMessage);
        verify(jwtDecoder, never()).decode(anyString());
    }

    // ── T-WS-01e (C2): SUBSCRIBE to own notification topic → allowed ──────────
    //
    // The principal set on CONNECT stores the userId as the JWT subject.
    // A SUBSCRIBE to /topic/notifications/{same-userId} must be allowed.

    @Test
    void preSend_subscribeToOwnTopic_isAllowed() {
        Message<?> subscribeMessage = buildSubscribeMessage(
                "/topic/notifications/" + USER_ID,
                USER_ID);

        Message<?> result = interceptor.preSend(subscribeMessage, channel);

        assertThat(result).isNotNull();
        verify(jwtDecoder, never()).decode(anyString());
    }

    // ── T-WS-01f (C2 RED/GREEN signal): SUBSCRIBE to another user's topic → AccessDeniedException
    //
    // Without the SUBSCRIBE branch in preSend, the check is never reached and no exception is
    // thrown — assertThatThrownBy FAILS (RED). With the fix in place, the destination userId
    // does not match the principal, so AccessDeniedException is thrown (GREEN).

    @Test
    void preSend_subscribeToAnotherUserTopic_throwsAccessDeniedException() {
        // Subscribe to OTHER_USER_ID's topic but the connected principal is USER_ID
        Message<?> subscribeMessage = buildSubscribeMessage(
                "/topic/notifications/" + OTHER_USER_ID,
                USER_ID);

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden");

        verify(jwtDecoder, never()).decode(anyString());
    }

    // ── C2 deny-by-default: wildcard SUBSCRIBE under /topic → rejected ────────
    //
    // The broker is enableSimpleBroker("/topic") with AntPathMatcher. A startsWith()
    // prefix check lets "/topic/**" through (it does NOT start with the literal
    // "/topic/notifications/" prefix). At delivery, convertAndSend("/topic/notifications/VICTIM")
    // matches "/topic/**" and the attacker receives ALL streams. Deny-by-default rejects this.

    @Test
    void preSend_subscribeWithDoubleWildcard_isRejected() {
        Message<?> subscribeMessage = buildSubscribeMessage("/topic/**", USER_ID);

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void preSend_subscribeWithSingleWildcardOnNotifications_isRejected() {
        Message<?> subscribeMessage = buildSubscribeMessage("/topic/notifications/*", USER_ID);

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void preSend_subscribeToBareTopicRoot_isRejected() {
        Message<?> subscribeMessage = buildSubscribeMessage("/topic/notifications", USER_ID);

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void preSend_subscribeWithNonUuidFinalSegment_isRejected() {
        Message<?> subscribeMessage = buildSubscribeMessage(
                "/topic/notifications/not-a-uuid", USER_ID);

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void preSend_subscribeToOtherTopicHierarchy_isRejected() {
        Message<?> subscribeMessage = buildSubscribeMessage("/topic/secrets", USER_ID);

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden");
    }

    // ── FIX #3: client SEND to a broker (/topic) destination → rejected ───────
    //
    // preSend previously only guarded CONNECT and SUBSCRIBE. A client could
    // SEND /topic/notifications/{victim} and inject fake notifications into any
    // user's stream. The server (SimpMessagingTemplate) is the only legitimate producer.

    @Test
    void preSend_sendToBrokerNotificationDestination_isRejected() {
        Message<?> sendMessage = buildSendMessage(
                "/topic/notifications/" + OTHER_USER_ID, USER_ID);

        assertThatThrownBy(() -> interceptor.preSend(sendMessage, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void preSend_sendToOwnBrokerNotificationDestination_isAlsoRejected() {
        // Even sending to your OWN topic is forbidden — clients must never produce to the broker.
        Message<?> sendMessage = buildSendMessage(
                "/topic/notifications/" + USER_ID, USER_ID);

        assertThatThrownBy(() -> interceptor.preSend(sendMessage, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden");
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

    /**
     * Builds a SUBSCRIBE message with the given destination and a pre-set principal
     * whose name (subject) is {@code principalUserId.toString()}.
     */
    private Message<?> buildSubscribeMessage(String destination, UUID principalUserId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        // Simulate the Authentication set on CONNECT — principal name is the JWT subject (userId)
        accessor.setUser(new java.security.Principal() {
            @Override
            public String getName() {
                return principalUserId.toString();
            }
        });
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /**
     * Builds a SEND message with the given destination and a pre-set principal.
     */
    private Message<?> buildSendMessage(String destination, UUID principalUserId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(new java.security.Principal() {
            @Override
            public String getName() {
                return principalUserId.toString();
            }
        });
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
