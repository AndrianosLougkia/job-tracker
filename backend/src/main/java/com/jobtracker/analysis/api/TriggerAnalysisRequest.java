package com.jobtracker.analysis.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class TriggerAnalysisRequest {
    @NotNull(message = "Application ID is required")
    @Positive(message = "Application ID must be positive")
    private Long applicationId;

    @NotNull(message = "Resume ID is required")
    @Positive(message = "Resume ID must be positive")
    private Long resumeId;

    public Long getApplicationId()          { return applicationId; }
    public void setApplicationId(Long id)   { this.applicationId = id; }
    public Long getResumeId()               { return resumeId; }
    public void setResumeId(Long id)        { this.resumeId = id; }
}
