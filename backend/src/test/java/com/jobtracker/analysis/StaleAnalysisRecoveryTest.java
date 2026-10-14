package com.jobtracker.analysis;

import com.jobtracker.analysis.domain.Analysis;
import com.jobtracker.analysis.domain.AnalysisRepository;
import com.jobtracker.analysis.domain.AnalysisStatus;
import com.jobtracker.analysis.infrastructure.StaleAnalysisRecovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaleAnalysisRecoveryTest {

    @Mock AnalysisRepository analysisRepository;

    @Test
    void unfinishedAnalysesAreMarkedFailedOnStartup() {
        Analysis pending = new Analysis(1L, 10L, 20L, "mock");
        Analysis processing = new Analysis(1L, 11L, 20L, "mock");
        processing.markProcessing();
        when(analysisRepository.findByStatusIn(EnumSet.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING)))
            .thenReturn(List.of(pending, processing));

        new StaleAnalysisRecovery(analysisRepository).failInterruptedAnalyses();

        assertThat(List.of(pending, processing)).allSatisfy(a -> {
            assertThat(a.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(a.getErrorMessage()).contains("server restart");
        });
        verify(analysisRepository).saveAll(List.of(pending, processing));
    }

    @Test
    void nothingToRecoverSavesNothing() {
        when(analysisRepository.findByStatusIn(any())).thenReturn(List.of());

        new StaleAnalysisRecovery(analysisRepository).failInterruptedAnalyses();

        verify(analysisRepository, never()).saveAll(any());
    }
}
