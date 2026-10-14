package com.jobtracker.notification.infrastructure;

import com.jobtracker.auth.infrastructure.JwtService;
import com.jobtracker.notification.domain.NotificationService;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates and authorises STOMP frames from clients.
 *
 * <ul>
 *   <li>CONNECT must carry {@code Authorization: Bearer <jwt>} as a STOMP header. The
 *       token is not accepted in the URL, where it would end up in access logs.</li>
 *   <li>SUBSCRIBE is allowed only to the caller's own user queue
 *       ({@value #ALLOWED_SUBSCRIPTION}); every other destination, including the
 *       resolved per-session broker queues, is refused.</li>
 *   <li>SEND is refused: the channel is server-to-client only.</li>
 * </ul>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String ALLOWED_SUBSCRIPTION = "/user" + NotificationService.ANALYSES_QUEUE;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public StompAuthChannelInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            if (accessor.getUser() == null) {
                throw new MessageDeliveryException("Not authenticated");
            }
            if (!ALLOWED_SUBSCRIPTION.equals(accessor.getDestination())) {
                log.warn("User {} tried to subscribe to {}", accessor.getUser().getName(), accessor.getDestination());
                throw new MessageDeliveryException("Subscription not allowed");
            }
        } else if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("Sending messages is not supported");
        }
        return message;
    }

    private StompPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        try {
            String email = jwtService.extractEmail(token);
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null && jwtService.isTokenValid(token, user)) {
                return new StompPrincipal(user.getId());
            }
        } catch (RuntimeException e) {
            log.debug("WebSocket token rejected: {}", e.getMessage());
        }
        throw new MessageDeliveryException("Invalid or expired token");
    }
}
