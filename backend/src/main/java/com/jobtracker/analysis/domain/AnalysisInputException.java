package com.jobtracker.analysis.domain;

/**
 * The referenced application/resume exist but cannot be analysed
 * (missing job description, no extracted resume text, input too long).
 */
public class AnalysisInputException extends RuntimeException {
    public AnalysisInputException(String message) { super(message); }
}
