package com.jobtracker.infrastructure.exception;

/**
 * Thrown when a requested resource does not exist OR belongs to another user.
 * Always maps to 404 — we do not distinguish "not found" from "not yours"
 * to avoid leaking resource existence to unauthorised callers.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException application(Long id) {
        return new ResourceNotFoundException("Application not found: " + id);
    }

    public static ResourceNotFoundException user(Long id) {
        return new ResourceNotFoundException("User not found: " + id);
    }
}
