package com.jobtracker.infrastructure.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
    public static ResourceNotFoundException application(Long id) {
        return new ResourceNotFoundException("Application not found: " + id);
    }
    public static ResourceNotFoundException user(Long id) {
        return new ResourceNotFoundException("User not found: " + id);
    }
}
