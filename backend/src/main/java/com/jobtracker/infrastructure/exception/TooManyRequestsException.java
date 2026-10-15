package com.jobtracker.infrastructure.exception;

import java.time.Duration;

/** A limit was hit (login attempts, daily analyses); answered with 429 and Retry-After. */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() { return retryAfter; }
}
