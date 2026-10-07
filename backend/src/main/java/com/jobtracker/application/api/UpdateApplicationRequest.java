package com.jobtracker.application.api;

import com.jobtracker.application.domain.ApplicationStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class UpdateApplicationRequest {
    @Size(max = 255) private String company;
    @Size(max = 255) private String role;
    private ApplicationStatus status;
    private String jobDescription;
    private String notes;
    private LocalDate appliedAt;

    public String getCompany()              { return company; }
    public void setCompany(String c)        { this.company = c; }
    public String getRole()                 { return role; }
    public void setRole(String r)           { this.role = r; }
    public ApplicationStatus getStatus()    { return status; }
    public void setStatus(ApplicationStatus s) { this.status = s; }
    public String getJobDescription()       { return jobDescription; }
    public void setJobDescription(String j) { this.jobDescription = j; }
    public String getNotes()                { return notes; }
    public void setNotes(String n)          { this.notes = n; }
    public LocalDate getAppliedAt()         { return appliedAt; }
    public void setAppliedAt(LocalDate d)   { this.appliedAt = d; }
}
