package com.jobtracker.resume.domain;

public class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) { super(message); }
}
