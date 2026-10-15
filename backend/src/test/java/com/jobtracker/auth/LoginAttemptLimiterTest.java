package com.jobtracker.auth;

import com.jobtracker.auth.domain.LoginAttemptLimiter;
import com.jobtracker.infrastructure.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

    private static final String EMAIL = "user@example.com";

    private Instant now = Instant.parse("2026-10-15T12:00:00Z");
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(15), clock);

    @Test
    void allowsAttemptsBelowTheLimit() {
        limiter.recordFailure(EMAIL);
        limiter.recordFailure(EMAIL);

        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void locksOutAtTheLimitWithTheRemainingTimeAsRetryAfter() {
        for (int i = 0; i < 3; i++) limiter.recordFailure(EMAIL);
        now = now.plus(Duration.ofMinutes(5));

        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL))
            .isInstanceOf(TooManyRequestsException.class)
            .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfter())
                .isEqualTo(Duration.ofMinutes(10)));
    }

    @Test
    void lockoutEndsWhenTheWindowHasPassed() {
        for (int i = 0; i < 3; i++) limiter.recordFailure(EMAIL);
        now = now.plus(Duration.ofMinutes(15));

        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void failuresOutsideTheWindowStartANewCount() {
        limiter.recordFailure(EMAIL);
        limiter.recordFailure(EMAIL);
        now = now.plus(Duration.ofMinutes(16));
        limiter.recordFailure(EMAIL);

        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void successClearsFailures() {
        limiter.recordFailure(EMAIL);
        limiter.recordFailure(EMAIL);
        limiter.recordSuccess(EMAIL);
        limiter.recordFailure(EMAIL);
        limiter.recordFailure(EMAIL);

        assertThatCode(() -> limiter.checkAllowed(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void lockoutIsPerEmail() {
        for (int i = 0; i < 3; i++) limiter.recordFailure(EMAIL);

        assertThatCode(() -> limiter.checkAllowed("other@example.com")).doesNotThrowAnyException();
    }
}
