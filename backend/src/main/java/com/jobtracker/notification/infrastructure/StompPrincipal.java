package com.jobtracker.notification.infrastructure;

import java.security.Principal;

/**
 * Identity of an authenticated WebSocket session. The name is the user ID, which is
 * what {@link com.jobtracker.notification.domain.NotificationService} addresses.
 */
public record StompPrincipal(Long userId) implements Principal {
    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
