package com.jobtracker.auth.domain;

import com.jobtracker.infrastructure.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after {@code max-failures} failed logins for one email
 * within {@code window}, further attempts for that email are refused with 429 until the
 * window has passed. A successful login clears the count.
 *
 * Keyed by email rather than client IP, so it works the same behind any reverse proxy.
 * State is in memory: it resets on restart and assumes a single backend instance.
 */
@Component
public class LoginAttemptLimiter {

    /** Above this many tracked emails, expired entries are purged so the map cannot grow unbounded. */
    private static final int PURGE_THRESHOLD = 10_000;

    private record Failures(int count, Instant windowStart) {}

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    public LoginAttemptLimiter(@Value("${app.auth.login.max-failures:5}") int maxFailures,
                               @Value("${app.auth.login.lockout:15m}") Duration window,
                               Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** Throws {@link TooManyRequestsException} if this email is currently locked out. */
    public void checkAllowed(String email) {
        Failures current = failures.get(email);
        if (current == null || current.count() < maxFailures) return;
        Duration remaining = Duration.between(clock.instant(), current.windowStart().plus(window));
        if (remaining.isNegative() || remaining.isZero()) {
            failures.remove(email, current);
            return;
        }
        throw new TooManyRequestsException(
            "Too many failed login attempts; try again in " + Math.max(1, remaining.toMinutes()) + " minutes",
            remaining);
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        if (failures.size() > PURGE_THRESHOLD) {
            failures.values().removeIf(f -> f.windowStart().plus(window).isBefore(now));
        }
        failures.compute(email, (key, current) ->
            current == null || current.windowStart().plus(window).isBefore(now)
                ? new Failures(1, now)
                : new Failures(current.count() + 1, current.windowStart()));
    }

    public void recordSuccess(String email) {
        failures.remove(email);
    }
}
