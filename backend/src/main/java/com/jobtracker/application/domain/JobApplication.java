package com.jobtracker.application.domain;

import com.jobtracker.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255) private String company;
    @Column(nullable = false, length = 255) private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "application_status")
    private ApplicationStatus status = ApplicationStatus.WISHLIST;

    @Column(name = "job_description", columnDefinition = "TEXT") private String jobDescription;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(name = "applied_at") private LocalDate appliedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected JobApplication() {}
    public JobApplication(User user, String company, String role) {
        this.user = user; this.company = company; this.role = role;
        this.status = ApplicationStatus.WISHLIST;
    }

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate  void onUpdate() { updatedAt = Instant.now(); }

    public Long getId()                  { return id; }
    public User getUser()                { return user; }
    public String getCompany()           { return company; }
    public String getRole()              { return role; }
    public ApplicationStatus getStatus() { return status; }
    public String getJobDescription()    { return jobDescription; }
    public String getNotes()             { return notes; }
    public LocalDate getAppliedAt()      { return appliedAt; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    public void setCompany(String company)               { this.company = company; }
    public void setRole(String role)                     { this.role = role; }
    public void setStatus(ApplicationStatus status)      { this.status = status; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public void setNotes(String notes)                   { this.notes = notes; }
    public void setAppliedAt(LocalDate appliedAt)        { this.appliedAt = appliedAt; }
}
