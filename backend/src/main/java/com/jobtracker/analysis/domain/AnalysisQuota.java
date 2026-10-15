package com.jobtracker.analysis.domain;

import com.jobtracker.infrastructure.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Caps how many analyses one user can start in a rolling 24 hours, so a paid AI
 * provider's bill stays predictable. Every started analysis counts, whatever its
 * outcome, because a failed provider call can still be billed. A limit of 0 turns
 * the cap off.
 *
 * The check and the insert are not atomic: two requests sent at the same instant can
 * both pass at the limit. For a spending guard that is an acceptable overshoot of one.
 */
@Component
public class AnalysisQuota {

    static final Duration WINDOW = Duration.ofHours(24);

    private final AnalysisRepository analysisRepository;
    private final int dailyLimit;
    private final Clock clock;

    public AnalysisQuota(AnalysisRepository analysisRepository,
                         @Value("${app.analysis.daily-limit:20}") int dailyLimit,
                         Clock clock) {
        this.analysisRepository = analysisRepository;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    /** Throws {@link TooManyRequestsException} if the user has used up the last 24 hours' allowance. */
    public void checkAllowed(Long userId) {
        if (dailyLimit <= 0) return;
        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);
        if (analysisRepository.countByUserIdAndCreatedAtAfter(userId, windowStart) < dailyLimit) return;

        // The allowance frees up when the oldest analysis in the window turns 24 hours old
        Duration retryAfter = analysisRepository
            .findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, windowStart)
            .map(oldest -> Duration.between(now, oldest.getCreatedAt().plus(WINDOW)))
            .orElse(WINDOW);
        throw new TooManyRequestsException(
            "Daily limit of " + dailyLimit + " analyses reached; try again in " + describe(retryAfter),
            retryAfter);
    }

    private static String describe(Duration d) {
        if (d.toHours() >= 1) return d.toHours() + (d.toHours() == 1 ? " hour" : " hours");
        long minutes = Math.max(1, d.toMinutes());
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
