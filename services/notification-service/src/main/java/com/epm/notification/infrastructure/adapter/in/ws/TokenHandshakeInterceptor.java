package com.epm.notification.infrastructure.adapter.in.ws;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket handshake interceptor that extracts the {@code ?token=...} query
 * parameter from the HTTP upgrade request and stores it in the WebSocket
 * session attributes.
 *
 * <p>This is needed because browsers cannot set custom HTTP headers on
 * WebSocket upgrade requests, so the JWT is passed as a query parameter.
 * The {@link WebSocketChannelInterceptor} then reads it from session attributes.
 */
public class TokenHandshakeInterceptor implements HandshakeInterceptor {

    private static final String TOKEN_PARAM = "token";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if (parts.length == 2 && TOKEN_PARAM.equals(parts[0])) {
                    attributes.put(TOKEN_PARAM, parts[1]);
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do after handshake
    }
}
