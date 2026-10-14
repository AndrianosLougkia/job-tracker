package com.jobtracker.analysis.infrastructure;

import com.jobtracker.analysis.domain.Analysis;
import com.jobtracker.analysis.domain.AnalysisRepository;
import com.jobtracker.analysis.domain.AnalysisStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;

/**
 * Background analyses live only in this process's executor, so a restart loses any
 * that had not finished. On startup, before new work arrives, those are marked
 * FAILED so they do not show as pending forever; the user can simply run them again.
 * Assumes a single backend instance, as the in-memory executor already does.
 */
@Component
public class StaleAnalysisRecovery {

    static final String INTERRUPTED_MESSAGE = "Interrupted by a server restart; please run the analysis again";

    private static final Logger log = LoggerFactory.getLogger(StaleAnalysisRecovery.class);

    private final AnalysisRepository analysisRepository;

    public StaleAnalysisRecovery(AnalysisRepository analysisRepository) {
        this.analysisRepository = analysisRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedAnalyses() {
        List<Analysis> stale = analysisRepository.findByStatusIn(
            EnumSet.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING));
        if (stale.isEmpty()) return;
        stale.forEach(analysis -> analysis.fail(INTERRUPTED_MESSAGE));
        analysisRepository.saveAll(stale);
        log.warn("Marked {} interrupted analyses as FAILED", stale.size());
    }
}
