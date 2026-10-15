package com.jobtracker.application.api;

import com.jobtracker.application.domain.ApplicationStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

import static com.jobtracker.application.api.CreateApplicationRequest.MAX_JOB_DESCRIPTION_CHARS;
import static com.jobtracker.application.api.CreateApplicationRequest.MAX_NOTES_CHARS;

/** Partial update: null fields are left unchanged; an empty description or notes clears it. */
public class UpdateApplicationRequest {
    private static final String NOT_BLANK = ".*\\S.*";

    @Size(max = 255, message = "Company name must not exceed 255 characters")
    @Pattern(regexp = NOT_BLANK, flags = Pattern.Flag.DOTALL, message = "Company name must not be blank")
    private String company;

    @Size(max = 255, message = "Role must not exceed 255 characters")
    @Pattern(regexp = NOT_BLANK, flags = Pattern.Flag.DOTALL, message = "Role must not be blank")
    private String role;

    private ApplicationStatus status;

    @Size(max = MAX_JOB_DESCRIPTION_CHARS, message = "Job description must not exceed 50000 characters")
    private String jobDescription;

    @Size(max = MAX_NOTES_CHARS, message = "Notes must not exceed 10000 characters")
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
