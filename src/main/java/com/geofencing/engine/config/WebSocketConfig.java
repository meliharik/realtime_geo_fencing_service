package com.geofencing.engine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration for Real-Time GPS Streaming
 *
 * Architecture:
 * - STOMP protocol over WebSocket
 * - In-memory message broker for pub/sub
 * - Multiple topic subscriptions
 *
 * Endpoints:
 * - /ws/gps-stream: WebSocket connection endpoint
 * - /app/*: Client messages (e.g., /app/gps for GPS data)
 * - /topic/*: Public broadcasts (e.g., /topic/alerts)
 * - /user/*: Private user-specific messages
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${geofencing.websocket.endpoint:/ws/gps-stream}")
    private String websocketEndpoint;

    @Value("${geofencing.websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${geofencing.websocket.topic-prefix:/topic}")
    private String topicPrefix;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory message broker
        // Destinations:
        // - /topic/* for public broadcasts (e.g., /topic/alerts, /topic/status)
        // - /user/* for private messages to specific users
        config.enableSimpleBroker("/topic", "/user");

        // Prefix for messages FROM clients TO server
        // Example: client sends to /app/gps
        config.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register WebSocket endpoint
        // Clients connect to: ws://localhost:8080/ws/gps-stream
        registry.addEndpoint(websocketEndpoint)
                .setAllowedOriginPatterns(allowedOrigins) // CORS configuration - allows wildcards
                .withSockJS(); // Fallback for browsers that don't support WebSocket

        // Also register without SockJS for native WebSocket clients
        registry.addEndpoint(websocketEndpoint)
                .setAllowedOriginPatterns(allowedOrigins); // CORS configuration
    }
}
