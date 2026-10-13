package com.jobtracker.analysis.api;

import com.jobtracker.ai.domain.AnalysisResult;
import com.jobtracker.analysis.domain.Analysis;
import com.jobtracker.analysis.domain.AnalysisStatus;
import java.time.Instant;
import java.util.List;

/**
 * Result fields (matchScore through summary) are null unless status is COMPLETED;
 * errorMessage is set only when status is FAILED.
 */
public class AnalysisResponse {
    private Long id;
    private Long applicationId;
    private Long resumeId;
    private AnalysisStatus status;
    private String provider;
    private Integer matchScore;
    private List<String> matchingSkills;
    private List<String> missingSkills;
    private List<String> strengths;
    private List<String> recommendations;
    private String summary;
    private String errorMessage;
    private Instant createdAt;
    private Instant completedAt;

    public static AnalysisResponse from(Analysis a) {
        AnalysisResponse r = new AnalysisResponse();
        r.id = a.getId();
        r.applicationId = a.getApplicationId();
        r.resumeId = a.getResumeId();
        r.status = a.getStatus();
        r.provider = a.getProvider();
        r.errorMessage = a.getErrorMessage();
        r.createdAt = a.getCreatedAt();
        r.completedAt = a.getCompletedAt();
        AnalysisResult result = a.getResult();
        if (result != null) {
            r.matchScore = result.matchScore();
            r.matchingSkills = result.matchingSkills();
            r.missingSkills = result.missingSkills();
            r.strengths = result.strengths();
            r.recommendations = result.recommendations();
            r.summary = result.summary();
        }
        return r;
    }

    public Long getId()                    { return id; }
    public Long getApplicationId()         { return applicationId; }
    public Long getResumeId()              { return resumeId; }
    public AnalysisStatus getStatus()      { return status; }
    public String getProvider()            { return provider; }
    public Integer getMatchScore()         { return matchScore; }
    public List<String> getMatchingSkills()  { return matchingSkills; }
    public List<String> getMissingSkills()   { return missingSkills; }
    public List<String> getStrengths()       { return strengths; }
    public List<String> getRecommendations() { return recommendations; }
    public String getSummary()             { return summary; }
    public String getErrorMessage()        { return errorMessage; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getCompletedAt()        { return completedAt; }
}
