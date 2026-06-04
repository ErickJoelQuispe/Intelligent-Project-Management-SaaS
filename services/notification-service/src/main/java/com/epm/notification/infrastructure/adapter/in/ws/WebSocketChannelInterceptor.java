package com.epm.notification.infrastructure.adapter.in.ws;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * STOMP channel interceptor that validates JWT tokens on CONNECT frames.
 *
 * <p>The JWT is expected in the session attributes under the key {@code "token"},
 * which is populated from the {@code ?token=...} query parameter on the WebSocket
 * upgrade request (see {@link WebSocketConfig}).
 *
 * <p>On CONNECT:
 * <ul>
 *   <li>Missing token → {@link AccessDeniedException}</li>
 *   <li>Invalid/expired token → {@link AccessDeniedException}</li>
 *   <li>Valid token → SecurityContext populated with authenticated user</li>
 * </ul>
 *
 * <p>All other STOMP frames (SUBSCRIBE, SEND, etc.) pass through unchanged.
 */
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChannelInterceptor.class);
    private static final String TOKEN_SESSION_ATTR = "token";

    private final JwtDecoder jwtDecoder;

    public WebSocketChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Not a CONNECT frame — pass through without validation
            return message;
        }

        String token = extractToken(accessor);
        if (token == null || token.isBlank()) {
            log.warn("WebSocket CONNECT rejected: Missing token");
            throw new AccessDeniedException("Missing token on STOMP CONNECT");
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            accessor.setUser(authentication);
            log.debug("WebSocket CONNECT accepted for user: {}", jwt.getSubject());
        } catch (JwtException ex) {
            log.warn("WebSocket CONNECT rejected: Invalid token — {}", ex.getMessage());
            throw new AccessDeniedException("Invalid token: " + ex.getMessage());
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object tokenObj = sessionAttributes.get(TOKEN_SESSION_ATTR);
            if (tokenObj instanceof String token) {
                return token;
            }
        }

        // Fallback: check native STOMP headers
        String nativeToken = accessor.getFirstNativeHeader("token");
        if (nativeToken != null && !nativeToken.isBlank()) {
            return nativeToken;
        }

        return null;
    }
}
