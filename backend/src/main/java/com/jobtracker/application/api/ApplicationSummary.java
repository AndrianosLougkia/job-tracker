package com.jobtracker.application.api;

import com.jobtracker.application.domain.ApplicationStatus;
import com.jobtracker.application.domain.JobApplication;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Compact application view for lists such as the dashboard. Leaves out the job
 * description and notes, which can be large and are not needed there.
 */
public record ApplicationSummary(
    Long id,
    String company,
    String role,
    ApplicationStatus status,
    LocalDate appliedAt,
    Instant createdAt
) {
    public static ApplicationSummary from(JobApplication app) {
        return new ApplicationSummary(app.getId(), app.getCompany(), app.getRole(), app.getStatus(),
            app.getAppliedAt(), app.getCreatedAt());
    }
}
