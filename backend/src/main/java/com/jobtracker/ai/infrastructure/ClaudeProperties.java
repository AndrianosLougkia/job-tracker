package com.jobtracker.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings for {@link ClaudeAiProvider}, bound from {@code app.ai.claude.*}.
 * Nothing here is required unless {@code app.ai.provider=claude}.
 *
 * @param apiKey     Anthropic API key; blank means Claude is not configured
 * @param baseUrl    API base URL
 * @param model      Claude model ID
 * @param effort     how much the model reasons before answering: low | medium | high | xhigh | max
 * @param maxTokens  upper bound on the response, including the model's reasoning
 * @param timeout    per-attempt request timeout
 * @param maxRetries retries for rate limits, overload, server errors and network failures
 */
@ConfigurationProperties("app.ai.claude")
public record ClaudeProperties(
    @DefaultValue("") String apiKey,
    @DefaultValue("https://api.anthropic.com") String baseUrl,
    @DefaultValue("claude-opus-5-5") String model,
    @DefaultValue("medium") String effort,
    @DefaultValue("16000") long maxTokens,
    @DefaultValue("120s") Duration timeout,
    @DefaultValue("2") int maxRetries
) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Never print the API key. */
    @Override
    public String toString() {
        return "ClaudeProperties[apiKey=" + (isConfigured() ? "****" : "<unset>") + ", baseUrl=" + baseUrl
            + ", model=" + model + ", effort=" + effort + ", maxTokens=" + maxTokens
            + ", timeout=" + timeout + ", maxRetries=" + maxRetries + "]";
    }
}
