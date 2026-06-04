package com.epm.notification.infrastructure.adapter.in.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * WebSocket/STOMP configuration for real-time notification delivery.
 *
 * <p>Exposes STOMP endpoint at {@code /ws/notifications} (no SockJS fallback).
 * Uses a simple in-memory message broker with prefix {@code /topic}.
 * JWT authentication is enforced via {@link WebSocketChannelInterceptor} on the
 * client inbound channel for CONNECT frames only.
 *
 * <p>The {@code ?token=...} query parameter is captured by
 * {@link HttpSessionHandshakeInterceptor} and stored as a session attribute,
 * where the interceptor can retrieve it.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;

    public WebSocketConfig(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new TokenHandshakeInterceptor());
        // No SockJS — Angular uses native WebSocket via @stomp/rx-stomp
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketChannelInterceptor());
    }

    @Bean
    WebSocketChannelInterceptor webSocketChannelInterceptor() {
        return new WebSocketChannelInterceptor(jwtDecoder);
    }
}
