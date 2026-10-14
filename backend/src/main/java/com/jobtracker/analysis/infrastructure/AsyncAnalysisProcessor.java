package com.jobtracker.analysis.infrastructure;

import com.jobtracker.ai.domain.AiProvider;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.domain.AiProviderFactory;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.analysis.domain.Analysis;
import com.jobtracker.analysis.domain.AnalysisRepository;
import com.jobtracker.analysis.domain.AnalysisStatus;
import com.jobtracker.infrastructure.config.AsyncConfig;
import com.jobtracker.notification.domain.AnalysisNotification;
import com.jobtracker.notification.domain.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs one analysis in the background: PENDING -> PROCESSING -> COMPLETED | FAILED,
 * then notifies the owner either way.
 *
 * Each status change is saved on its own, so the AI call never holds a database
 * transaction open and a FAILED analysis is persisted even when the provider throws.
 * Nothing escapes {@link #process}: on a background thread an uncaught exception
 * would only be logged and the analysis would stay stuck in PROCESSING.
 */
@Component
public class AsyncAnalysisProcessor {

    static final String UNEXPECTED_FAILURE_MESSAGE = "Unexpected error while running AI analysis";

    private static final Logger log = LoggerFactory.getLogger(AsyncAnalysisProcessor.class);

    private final AnalysisRepository analysisRepository;
    private final AiProviderFactory providerFactory;
    private final NotificationService notificationService;

    public AsyncAnalysisProcessor(AnalysisRepository analysisRepository,
                                  AiProviderFactory providerFactory,
                                  NotificationService notificationService) {
        this.analysisRepository = analysisRepository;
        this.providerFactory = providerFactory;
        this.notificationService = notificationService;
    }

    @Async(AsyncConfig.ANALYSIS_EXECUTOR)
    public void process(Long analysisId, AnalysisRequest request) {
        Analysis analysis = analysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) {
            log.info("Analysis {} was deleted before processing started", analysisId);
            return;
        }
        if (analysis.getStatus() != AnalysisStatus.PENDING) {
            log.warn("Analysis {} is {}, not PENDING; skipping", analysisId, analysis.getStatus());
            return;
        }
        try {
            analysis.markProcessing();
            analysis = analysisRepository.save(analysis);
            runProvider(analysis, request);
            analysis = analysisRepository.save(analysis);
        } catch (RuntimeException e) {
            // Persistence failed, e.g. the application was deleted mid-analysis and the
            // row went with it. Nothing left to update or report.
            log.warn("Could not save analysis {}: {}", analysisId, e.getMessage());
            return;
        }
        notifyOwner(analysis);
    }

    private void runProvider(Analysis analysis, AnalysisRequest request) {
        AiProvider provider = providerFactory.getProvider();
        try {
            analysis.complete(provider.analyze(request));
            log.debug("Analysis {} completed with score {}", analysis.getId(), analysis.getMatchScore());
        } catch (AiProviderException e) {
            log.warn("AI provider '{}' failed for analysis {}: {}", provider.name(), analysis.getId(), e.getMessage());
            analysis.fail(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected failure in AI provider '{}' for analysis {}", provider.name(), analysis.getId(), e);
            analysis.fail(UNEXPECTED_FAILURE_MESSAGE);
        }
    }

    private void notifyOwner(Analysis analysis) {
        boolean completed = analysis.getStatus() == AnalysisStatus.COMPLETED;
        String message = completed
            ? "Analysis complete: " + analysis.getMatchScore() + "% match"
            : "Analysis failed: " + analysis.getErrorMessage();
        notificationService.notifyAnalysisFinished(analysis.getUserId(), new AnalysisNotification(
            analysis.getId(), analysis.getApplicationId(), analysis.getStatus().name(),
            analysis.getMatchScore(), message));
    }
}
