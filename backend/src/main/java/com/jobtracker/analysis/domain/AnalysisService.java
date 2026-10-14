package com.jobtracker.analysis.domain;

import com.jobtracker.ai.domain.AiProviderFactory;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.analysis.api.AnalysisResponse;
import com.jobtracker.analysis.infrastructure.AsyncAnalysisProcessor;
import com.jobtracker.application.domain.ApplicationService;
import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import com.jobtracker.resume.domain.ResumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Accepts resume-versus-job-description analyses and serves their results.
 * Processing itself happens in {@link AsyncAnalysisProcessor}.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    static final String QUEUE_FULL_MESSAGE = "Too many analyses are running; please try again in a few minutes";

    private final AnalysisRepository analysisRepository;
    private final ApplicationService applicationService;
    private final ResumeService resumeService;
    private final AiProviderFactory providerFactory;
    private final AsyncAnalysisProcessor processor;
    private final int maxInputChars;

    public AnalysisService(AnalysisRepository analysisRepository,
                           ApplicationService applicationService,
                           ResumeService resumeService,
                           AiProviderFactory providerFactory,
                           AsyncAnalysisProcessor processor,
                           @Value("${app.ai.max-input-chars:50000}") int maxInputChars) {
        this.analysisRepository = analysisRepository;
        this.applicationService = applicationService;
        this.resumeService = resumeService;
        this.providerFactory = providerFactory;
        this.processor = processor;
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
     * Start analysing a resume against an application's job description.
     * Both must belong to the user (404 otherwise) and contain usable text (422 otherwise);
     * those checks run in the request. The AI call runs in the background, so this returns
     * the analysis as PENDING and the owner is notified over WebSocket when it finishes.
     *
     * Not transactional on purpose: the PENDING row must be committed before the
     * background thread looks it up.
     */
    public AnalysisResponse requestAnalysis(Long applicationId, Long resumeId, Long userId) {
        // Ownership checks happen inside the owning modules' services.
        String jobDescription = applicationService.getJobDescription(applicationId, userId);
        String resumeText = resumeService.getExtractedText(resumeId, userId);

        requireUsableText(jobDescription, "Application " + applicationId + " has no job description to analyse",
            "Job description");
        requireUsableText(resumeText, "Resume " + resumeId + " has no extracted text to analyse",
            "Resume text");

        String providerName = providerFactory.getProvider().name();
        Analysis analysis = analysisRepository.save(new Analysis(userId, applicationId, resumeId, providerName));

        try {
            processor.process(analysis.getId(), new AnalysisRequest(resumeText, jobDescription));
        } catch (TaskRejectedException e) {
            log.warn("Analysis queue is full; rejecting analysis {}", analysis.getId());
            analysis.fail(QUEUE_FULL_MESSAGE);
            analysis = analysisRepository.save(analysis);
        }
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
