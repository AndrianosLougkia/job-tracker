package com.jobtracker.ai.domain;

/**
 * Abstraction over an AI backend that compares a resume against a job description.
 * Implementations live in ai.infrastructure; callers obtain the active one from
 * {@link AiProviderFactory} and never depend on a concrete provider.
 */
public interface AiProvider {

    /**
     * Stable, lowercase identifier used for selection via {@code app.ai.provider}
     * (e.g. "mock", "claude", "ollama"). Also recorded on each persisted analysis.
     */
    String name();

    /**
     * Run the analysis.
     * @throws AiProviderException if the provider is unavailable or returns an unusable response
     */
    AnalysisResult analyze(AnalysisRequest request);
}
