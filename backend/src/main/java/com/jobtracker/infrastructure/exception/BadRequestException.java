package com.jobtracker.infrastructure.exception;

/** Input that passed bean validation but is still unacceptable; answered with 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
