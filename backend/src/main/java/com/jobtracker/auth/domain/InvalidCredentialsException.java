package com.jobtracker.auth.domain;

/**
 * Thrown when login credentials are invalid.
 * Maps to 401 Unauthorized.
 * The message is intentionally generic to avoid confirming whether the email exists.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
