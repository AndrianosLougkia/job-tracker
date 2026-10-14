package com.jobtracker.notification.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes per-user notifications over STOMP.
 *
 * Messages go to the user destination {@value #ANALYSES_QUEUE}; Spring routes them
 * only to the WebSocket sessions whose principal is that user, so clients subscribe
 * to {@code /user/queue/analyses} and never name another user in a destination.
 * Delivery is best effort: a user with no open session simply misses the message
 * and sees the result the next time the page loads it over REST.
 */
@Service
public class NotificationService {

    public static final String ANALYSES_QUEUE = "/queue/analyses";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyAnalysisFinished(Long userId, AnalysisNotification notification) {
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), ANALYSES_QUEUE, notification);
        } catch (RuntimeException e) {
            // A notification failure must never undo or fail the analysis itself.
            log.warn("Could not notify user {} about analysis {}: {}",
                userId, notification.analysisId(), e.getMessage());
        }
    }
}
