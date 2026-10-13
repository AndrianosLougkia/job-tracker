package com.jobtracker.analysis.domain;

/**
 * The AI provider could not produce a result. The analysis has already been
 * persisted as FAILED, so clients can still look it up by {@link #getAnalysisId()}.
 */
public class AnalysisFailedException extends RuntimeException {

    private final Long analysisId;

    public AnalysisFailedException(Long analysisId, String reason) {
        super("AI analysis failed: " + reason);
        this.analysisId = analysisId;
    }

    public Long getAnalysisId() { return analysisId; }
}
