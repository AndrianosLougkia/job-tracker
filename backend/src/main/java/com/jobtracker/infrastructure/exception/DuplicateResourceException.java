package com.jobtracker.infrastructure.exception;

/**
 * Thrown when a uniqueness constraint would be violated (e.g. duplicate email).
 * Maps to 409 Conflict.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
