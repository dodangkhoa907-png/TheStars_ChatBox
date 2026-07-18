package com.thestars.chatbox.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * WebSocket configuration using STOMP protocol.
 * Enables real-time bidirectional messaging.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory broker for broadcasting
        // /topic — broadcast to all subscribers (e.g., conversation messages)
        // /queue — private messages to specific users
        config.enableSimpleBroker("/topic", "/queue");

        // Prefix for messages FROM client → server (handled by @MessageMapping)
        config.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint that clients connect to
        // SockJS fallback for browsers that don't support WebSocket
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    // The default inbound/outbound channel pools size themselves off available
    // CPU cores, which on a small hosting instance can be just 1-2 — nowhere
    // near enough to keep up with many concurrent WebSocket sessions sending
    // messages, typing indicators, and heartbeats. Give both a fixed floor.
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(16)
                .maxPoolSize(64)
                .queueCapacity(500);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(16)
                .maxPoolSize(64)
                .queueCapacity(500);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(15_000).setSendBufferSizeLimit(512 * 1024);
    }
}
