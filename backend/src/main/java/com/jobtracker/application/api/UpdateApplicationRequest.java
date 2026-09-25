package com.jobtracker.application.api;

import com.jobtracker.application.domain.ApplicationStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body for updating an existing job application.
 * Any null field is left unchanged (partial update semantics).
 */
public class UpdateApplicationRequest {

    @Size(max = 255, message = "Company name must not exceed 255 characters")
    private String company;

    @Size(max = 255, message = "Role must not exceed 255 characters")
    private String role;

    private ApplicationStatus status;

    private String jobDescription;

    private String notes;

    private LocalDate appliedAt;

    // Getters and setters

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDate getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDate appliedAt) { this.appliedAt = appliedAt; }
}
