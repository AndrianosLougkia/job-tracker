package com.jobtracker.application.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class CreateApplicationRequest {
    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name must not exceed 255 characters")
    private String company;

    @NotBlank(message = "Role is required")
    @Size(max = 255, message = "Role must not exceed 255 characters")
    private String role;

    private String jobDescription;
    private String notes;
    private LocalDate appliedAt;

    public String getCompany()           { return company; }
    public void setCompany(String c)     { this.company = c; }
    public String getRole()              { return role; }
    public void setRole(String r)        { this.role = r; }
    public String getJobDescription()    { return jobDescription; }
    public void setJobDescription(String j) { this.jobDescription = j; }
    public String getNotes()             { return notes; }
    public void setNotes(String n)       { this.notes = n; }
    public LocalDate getAppliedAt()      { return appliedAt; }
    public void setAppliedAt(LocalDate d){ this.appliedAt = d; }
}
