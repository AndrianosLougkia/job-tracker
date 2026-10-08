package com.jobtracker.resume.domain;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException(String message) { super(message); }
}
