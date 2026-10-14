package com.jobtracker.notification.domain;

/**
 * Pushed to the owning user when an analysis finishes. Carries just enough for the
 * UI to show a message and refresh; the full result is fetched over REST, which
 * keeps resume-derived content out of the notification channel.
 *
 * @param status     COMPLETED or FAILED
 * @param matchScore set only when COMPLETED
 */
public record AnalysisNotification(
    Long analysisId,
    Long applicationId,
    String status,
    Integer matchScore,
    String message
) {}
