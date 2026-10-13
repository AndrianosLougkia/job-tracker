package com.jobtracker.analysis.domain;

import com.jobtracker.ai.domain.AnalysisResult;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * One resume-versus-job-description analysis run.
 *
 * Related user, application and resume are held as plain IDs rather than JPA
 * associations so this module never touches another module's entities.
 * Status changes only through the transition methods, which enforce
 * PENDING -> PROCESSING -> COMPLETED | FAILED.
 */
@Entity
@Table(name = "analyses")
public class Analysis {

    public static final int MAX_ERROR_LENGTH = 1000;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)        private Long userId;
    @Column(name = "application_id", nullable = false, updatable = false) private Long applicationId;
    @Column(name = "resume_id", nullable = false, updatable = false)      private Long resumeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "analysis_status")
    private AnalysisStatus status = AnalysisStatus.PENDING;

    @Column(nullable = false, length = 50, updatable = false) private String provider;
    @Column(name = "match_score") private Integer matchScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private AnalysisResult result;

    @Column(name = "error_message", length = MAX_ERROR_LENGTH) private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected Analysis() {}
    public Analysis(Long userId, Long applicationId, Long resumeId, String provider) {
        this.userId = userId;
        this.applicationId = applicationId;
        this.resumeId = resumeId;
        this.provider = provider;
        this.status = AnalysisStatus.PENDING;
    }

    @PrePersist void onCreate() { createdAt = Instant.now(); }

    // ------------------------------------------------------------------ //
    //  State transitions                                                  //
    // ------------------------------------------------------------------ //

    public void markProcessing() {
        requireStatus(AnalysisStatus.PENDING);
        status = AnalysisStatus.PROCESSING;
    }

    public void complete(AnalysisResult result) {
        requireStatus(AnalysisStatus.PROCESSING);
        if (result == null) throw new IllegalArgumentException("result must not be null");
        this.result = result;
        this.matchScore = result.matchScore();
        this.status = AnalysisStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        if (status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED) {
            throw new IllegalStateException("Analysis " + id + " is already " + status);
        }
        String message = errorMessage == null || errorMessage.isBlank() ? "Analysis failed" : errorMessage;
        this.errorMessage = message.length() > MAX_ERROR_LENGTH
            ? message.substring(0, MAX_ERROR_LENGTH) : message;
        this.status = AnalysisStatus.FAILED;
        this.completedAt = Instant.now();
    }

    private void requireStatus(AnalysisStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                "Analysis " + id + " must be " + expected + " but is " + status);
        }
    }

    public Long getId()               { return id; }
    public Long getUserId()           { return userId; }
    public Long getApplicationId()    { return applicationId; }
    public Long getResumeId()         { return resumeId; }
    public AnalysisStatus getStatus() { return status; }
    public String getProvider()       { return provider; }
    public Integer getMatchScore()    { return matchScore; }
    public AnalysisResult getResult() { return result; }
    public String getErrorMessage()   { return errorMessage; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getCompletedAt()   { return completedAt; }
}
