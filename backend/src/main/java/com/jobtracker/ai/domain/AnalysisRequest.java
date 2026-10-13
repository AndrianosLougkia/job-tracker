package com.jobtracker.ai.domain;

/**
 * Provider-agnostic input to an AI analysis.
 * Callers are responsible for user-facing validation; this only guards the invariant.
 */
public record AnalysisRequest(String resumeText, String jobDescription) {

    public AnalysisRequest {
        if (resumeText == null || resumeText.isBlank()) {
            throw new IllegalArgumentException("resumeText must not be blank");
        }
        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("jobDescription must not be blank");
        }
    }

    /** Keep resume and job text out of logs. */
    @Override
    public String toString() {
        return "AnalysisRequest[resumeText=" + resumeText.length() + " chars, jobDescription="
            + jobDescription.length() + " chars]";
    }
}
