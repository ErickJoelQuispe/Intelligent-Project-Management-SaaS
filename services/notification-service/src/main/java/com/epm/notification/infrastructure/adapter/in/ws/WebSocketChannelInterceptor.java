package com.epm.notification.infrastructure.adapter.in.ws;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * STOMP channel interceptor that validates JWT tokens on CONNECT frames and enforces
 * per-user SUBSCRIBE authorization (C2 — topic-hijacking fix).
 *
 * <p>The JWT is expected in the session attributes under the key {@code "token"},
 * which is populated from the {@code ?token=...} query parameter on the WebSocket
 * upgrade request (see {@link WebSocketConfig}).
 *
 * <p>On CONNECT:
 * <ul>
 *   <li>Missing token → {@link AccessDeniedException}</li>
 *   <li>Invalid/expired token → {@link AccessDeniedException}</li>
 *   <li>Valid token → principal set on the STOMP session via {@code accessor.setUser(...)}
 *       (NOT on the thread-local {@code SecurityContextHolder} — see FIX C below)</li>
 * </ul>
 *
 * <p>On SUBSCRIBE (C2 — deny-by-default under {@code /topic}):
 * <ul>
 *   <li>Any destination under the broker prefix {@code /topic} is REJECTED unless it is
 *       <em>exactly</em> {@code /topic/notifications/{uuid}} where {@code {uuid}} parses as a
 *       valid UUID AND equals the connected principal's name (JWT {@code sub}). This closes the
 *       wildcard-hijack bypass: an attacker subscribing to {@code /topic/**} or
 *       {@code /topic/notifications/*} previously slipped past a {@code startsWith} prefix check
 *       yet matched the AntPathMatcher broker pattern at delivery, receiving every user's stream.</li>
 *   <li>Destinations NOT under {@code /topic} (e.g. {@code /user/**}, {@code /queue/**}) pass
 *       through unchanged.</li>
 * </ul>
 *
 * <p>On SEND (FIX #3 — no client production to the broker):
 * <ul>
 *   <li>Any client {@code SEND} to a destination under {@code /topic} is REJECTED. The server
 *       ({@code SimpMessagingTemplate}) is the sole legitimate producer of broker messages;
 *       allowing client SENDs would let a client inject fake notifications into any user's stream.</li>
 * </ul>
 *
 * <p>All other STOMP frames pass through unchanged.
 */
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChannelInterceptor.class);
    private static final String TOKEN_SESSION_ATTR = "token";

    /** Broker destination prefix (matches {@code enableSimpleBroker("/topic")}). */
    private static final String BROKER_PREFIX = "/topic";

    /** Prefix for per-user notification topics (matches StompNotificationPushAdapter). */
    private static final String NOTIFICATION_TOPIC_PREFIX = "/topic/notifications/";

    private final JwtDecoder jwtDecoder;

    public WebSocketChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        // ── CONNECT: validate JWT, populate principal ─────────────────────────
        if (StompCommand.CONNECT.equals(command)) {
            return handleConnect(message, accessor);
        }

        // ── SUBSCRIBE: deny-by-default per-user topic authorization (C2) ──────
        if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        }

        // ── SEND: clients must never produce to broker destinations (FIX #3) ──
        if (StompCommand.SEND.equals(command)) {
            handleSend(accessor);
        }

        return message;
    }

    // ── Private handlers ──────────────────────────────────────────────────────

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        if (token == null || token.isBlank()) {
            log.warn("WebSocket CONNECT rejected: Missing token");
            throw new AccessDeniedException("Missing token on STOMP CONNECT");
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
            // FIX C: propagate the principal ONLY on the STOMP accessor/session. Do NOT populate the
            // thread-local SecurityContextHolder here — STOMP frames run on a reused thread pool and
            // the thread-local is never cleared, so it would leak the previous user's context into a
            // later frame from a different session on the same thread. SUBSCRIBE authorization reads
            // accessor.getUser(), so the accessor principal is the single source of WebSocket identity.
            accessor.setUser(authentication);
            log.debug("WebSocket CONNECT accepted for user: {}", jwt.getSubject());
        } catch (JwtException ex) {
            log.warn("WebSocket CONNECT rejected: Invalid token — {}", ex.getMessage());
            throw new AccessDeniedException("Invalid token: " + ex.getMessage());
        }

        return message;
    }

    /**
     * Enforces deny-by-default authorization for SUBSCRIBE frames targeting the broker (C2).
     *
     * <p>Any destination under {@code /topic} is REJECTED unless it is exactly
     * {@code /topic/notifications/{uuid}} where {@code {uuid}}:
     * <ul>
     *   <li>contains no STOMP/Ant metacharacters ({@code * ? { } ,}),</li>
     *   <li>parses as a valid {@link UUID}, and</li>
     *   <li>equals the connected principal's name (JWT {@code sub}).</li>
     * </ul>
     * Destinations not under {@code /topic} pass through (e.g. {@code /user/**}).
     *
     * @throws AccessDeniedException if the SUBSCRIBE is not an exact, authorized per-user topic
     */
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        // Only the broker hierarchy is constrained; anything else is out of scope here.
        if (destination == null || !isUnderBroker(destination)) {
            return;
        }

        // Deny-by-default: from here on, the ONLY acceptable shape is the exact per-user topic.
        if (!destination.startsWith(NOTIFICATION_TOPIC_PREFIX)) {
            reject("SUBSCRIBE", destination, "not a per-user notification topic");
        }

        // Reject metacharacters anywhere in the destination (wildcards, selectors, braces).
        if (containsMetacharacters(destination)) {
            reject("SUBSCRIBE", destination, "destination contains disallowed metacharacters");
        }

        // The remainder after the prefix must be a single segment that is a valid UUID.
        String finalSegment = destination.substring(NOTIFICATION_TOPIC_PREFIX.length());
        if (finalSegment.isEmpty() || finalSegment.indexOf('/') >= 0) {
            reject("SUBSCRIBE", destination, "destination is not a single UUID segment");
        }

        UUID destinationUserId = parseUuidOrReject("SUBSCRIBE", destination, finalSegment);

        java.security.Principal principal = accessor.getUser();
        if (principal == null) {
            reject("SUBSCRIBE", destination, "no authenticated principal");
            return; // unreachable — reject() always throws; explicit for compiler safety
        }

        String connectedUserId = principal.getName();
        if (!destinationUserId.toString().equals(connectedUserId)) {
            log.warn("WebSocket SUBSCRIBE rejected: principal={} attempted to subscribe to topic of userId={}",
                    connectedUserId, destinationUserId);
            throw new AccessDeniedException(
                    "Forbidden: cannot subscribe to another user's notification topic");
        }

        log.debug("WebSocket SUBSCRIBE authorized: userId={} destination={}", connectedUserId, destination);
    }

    /**
     * Rejects any client SEND to a broker destination (FIX #3). The server is the only
     * legitimate producer to {@code /topic}; client SENDs could inject fake notifications.
     *
     * @throws AccessDeniedException if the SEND targets a destination under {@code /topic}
     */
    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination != null && isUnderBroker(destination)) {
            reject("SEND", destination, "clients must not SEND to broker destinations");
        }
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the destination is the broker root {@code /topic} or any
     * destination beneath it ({@code /topic/...}).
     */
    private static boolean isUnderBroker(String destination) {
        return destination.equals(BROKER_PREFIX) || destination.startsWith(BROKER_PREFIX + "/");
    }

    /** Detects STOMP/Ant metacharacters that must never appear in an exact per-user topic. */
    private static boolean containsMetacharacters(String destination) {
        return destination.indexOf('*') >= 0
                || destination.indexOf('?') >= 0
                || destination.indexOf('{') >= 0
                || destination.indexOf('}') >= 0
                || destination.indexOf(',') >= 0;
    }

    private UUID parseUuidOrReject(String frame, String destination, String candidate) {
        try {
            return UUID.fromString(candidate);
        } catch (IllegalArgumentException ex) {
            reject(frame, destination, "final segment is not a valid UUID");
            return null; // unreachable — reject() always throws
        }
    }

    private void reject(String frame, String destination, String reason) {
        log.warn("WebSocket {} rejected: {} (destination={})", frame, reason, destination);
        throw new AccessDeniedException("Forbidden: " + reason);
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
