package com.jobtracker.ai.domain;

/**
 * Raised when an AI provider cannot produce a usable result
 * (unreachable, timed out, or returned a malformed response).
 * Messages must be safe to persist and show to the user — never include
 * API keys, raw prompts, or resume content.
 */
public class AiProviderException extends RuntimeException {
    public AiProviderException(String message) { super(message); }
    public AiProviderException(String message, Throwable cause) { super(message, cause); }
}
