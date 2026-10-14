package com.jobtracker.notification.infrastructure;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket at {@code /api/ws} with Spring's in-memory broker.
 *
 * Topic design: the only destination clients can use is {@code /user/queue/analyses}
 * (see {@link StompAuthChannelInterceptor}). The in-memory broker suits a single
 * backend instance; running several would need a shared broker relay.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_MS = 10_000;

    private final StompAuthChannelInterceptor authInterceptor;
    private final String[] allowedOrigins;
    private final ThreadPoolTaskScheduler heartbeatScheduler;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor,
                           @Value("${app.websocket.allowed-origins}") String[] allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = allowedOrigins;
        this.heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(1);
        heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
        heartbeatScheduler.initialize();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Heartbeats let both sides notice dead connections (e.g. a laptop going to sleep).
        registry.enableSimpleBroker("/queue")
            .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
            .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @PreDestroy
    void shutdown() {
        heartbeatScheduler.shutdown();
    }
}
