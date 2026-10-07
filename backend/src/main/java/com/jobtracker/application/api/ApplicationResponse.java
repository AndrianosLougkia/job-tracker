package com.jobtracker.application.api;

import com.jobtracker.application.domain.ApplicationStatus;
import com.jobtracker.application.domain.JobApplication;
import java.time.Instant;
import java.time.LocalDate;

public class ApplicationResponse {
    private Long id;
    private Long userId;
    private String company;
    private String role;
    private ApplicationStatus status;
    private String jobDescription;
    private String notes;
    private LocalDate appliedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static ApplicationResponse from(JobApplication app) {
        ApplicationResponse r = new ApplicationResponse();
        r.id = app.getId();
        r.userId = app.getUser().getId();
        r.company = app.getCompany();
        r.role = app.getRole();
        r.status = app.getStatus();
        r.jobDescription = app.getJobDescription();
        r.notes = app.getNotes();
        r.appliedAt = app.getAppliedAt();
        r.createdAt = app.getCreatedAt();
        r.updatedAt = app.getUpdatedAt();
        return r;
    }

    public Long getId()                  { return id; }
    public Long getUserId()              { return userId; }
    public String getCompany()           { return company; }
    public String getRole()              { return role; }
    public ApplicationStatus getStatus() { return status; }
    public String getJobDescription()    { return jobDescription; }
    public String getNotes()             { return notes; }
    public LocalDate getAppliedAt()      { return appliedAt; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
}
