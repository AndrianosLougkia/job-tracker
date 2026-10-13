package com.jobtracker.analysis.domain;

import com.jobtracker.ai.domain.AiProvider;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.domain.AiProviderFactory;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.domain.AnalysisResult;
import com.jobtracker.analysis.api.AnalysisResponse;
import com.jobtracker.application.domain.ApplicationService;
import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import com.jobtracker.resume.domain.ResumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Runs resume-versus-job-description analyses and manages their lifecycle.
 *
 * Deliberately not transactional at class level: the AI call can be slow and must
 * not hold a database transaction open, and each status change is committed on its
 * own so a FAILED analysis is still persisted when the request ends in an error.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    static final String UNEXPECTED_FAILURE_MESSAGE = "Unexpected error while running AI analysis";

    private final AnalysisRepository analysisRepository;
    private final ApplicationService applicationService;
    private final ResumeService resumeService;
    private final AiProviderFactory providerFactory;
    private final int maxInputChars;

    public AnalysisService(AnalysisRepository analysisRepository,
                           ApplicationService applicationService,
                           ResumeService resumeService,
                           AiProviderFactory providerFactory,
                           @Value("${app.ai.max-input-chars:50000}") int maxInputChars) {
        this.analysisRepository = analysisRepository;
        this.applicationService = applicationService;
        this.resumeService = resumeService;
        this.providerFactory = providerFactory;
        this.maxInputChars = maxInputChars;
    }

    // ------------------------------------------------------------------ //
    //  Queries                                                            //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public List<AnalysisResponse> listAnalyses(Long userId, Long applicationId) {
        List<Analysis> analyses = applicationId == null
            ? analysisRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId)
            : analysisRepository.findByUserIdAndApplicationIdOrderByCreatedAtDescIdDesc(userId, applicationId);
        return analyses.stream().map(AnalysisResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysis(Long id, Long userId) {
        return analysisRepository.findByIdAndUserId(id, userId)
            .map(AnalysisResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("Analysis not found: " + id));
    }

    // ------------------------------------------------------------------ //
    //  Commands                                                           //
    // ------------------------------------------------------------------ //

    /**
     * Analyse a resume against an application's job description.
     * Both must belong to the user (404 otherwise) and contain usable text (422 otherwise).
     * Runs synchronously; Stage 8 moves the provider call onto an async executor.
     *
     * @throws AnalysisFailedException if the provider fails; the FAILED analysis is persisted first
     */
    public AnalysisResponse analyze(Long applicationId, Long resumeId, Long userId) {
        // Ownership checks happen inside the owning modules' services.
        String jobDescription = applicationService.getJobDescription(applicationId, userId);
        String resumeText = resumeService.getExtractedText(resumeId, userId);

        requireUsableText(jobDescription, "Application " + applicationId + " has no job description to analyse",
            "Job description");
        requireUsableText(resumeText, "Resume " + resumeId + " has no extracted text to analyse",
            "Resume text");

        AiProvider provider = providerFactory.getProvider();
        Analysis analysis = new Analysis(userId, applicationId, resumeId, provider.name());
        analysis.markProcessing();
        analysis = analysisRepository.save(analysis);

        try {
            AnalysisResult result = provider.analyze(new AnalysisRequest(resumeText, jobDescription));
            analysis.complete(result);
        } catch (AiProviderException e) {
            log.warn("AI provider '{}' failed for analysis {}: {}", provider.name(), analysis.getId(), e.getMessage());
            analysis.fail(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected failure in AI provider '{}' for analysis {}", provider.name(), analysis.getId(), e);
            analysis.fail(UNEXPECTED_FAILURE_MESSAGE);
        }

        analysis = analysisRepository.save(analysis);
        if (analysis.getStatus() == AnalysisStatus.FAILED) {
            throw new AnalysisFailedException(analysis.getId(), analysis.getErrorMessage());
        }
        log.debug("Analysis {} completed with score {}", analysis.getId(), analysis.getMatchScore());
        return AnalysisResponse.from(analysis);
    }

    private void requireUsableText(String text, String missingMessage, String label) {
        if (text == null || text.isBlank()) {
            throw new AnalysisInputException(missingMessage);
        }
        if (text.length() > maxInputChars) {
            throw new AnalysisInputException(
                label + " exceeds the maximum of " + maxInputChars + " characters for analysis");
        }
    }
}
