package com.jobtracker.analysis;

import com.jobtracker.ai.domain.AnalysisResult;
import com.jobtracker.analysis.domain.Analysis;
import com.jobtracker.analysis.domain.AnalysisStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisTest {

    private static final AnalysisResult RESULT =
        new AnalysisResult(70, List.of("Java"), List.of("Go"), List.of(), List.of(), "ok");

    private final Analysis analysis = new Analysis(1L, 2L, 3L, "mock");

    @Test
    void startsPending() {
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(analysis.getResult()).isNull();
        assertThat(analysis.getCompletedAt()).isNull();
    }

    @Test
    void completesAfterProcessing() {
        analysis.markProcessing();
        analysis.complete(RESULT);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(analysis.getResult()).isEqualTo(RESULT);
        assertThat(analysis.getMatchScore()).isEqualTo(70);
        assertThat(analysis.getCompletedAt()).isNotNull();
        assertThat(analysis.getErrorMessage()).isNull();
    }

    @Test
    void cannotCompleteWithoutProcessing() {
        assertThatThrownBy(() -> analysis.complete(RESULT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotProcessTwice() {
        analysis.markProcessing();
        assertThatThrownBy(analysis::markProcessing).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failRecordsMessageAndTruncatesIt() {
        analysis.markProcessing();
        analysis.fail("x".repeat(Analysis.MAX_ERROR_LENGTH + 50));

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getErrorMessage()).hasSize(Analysis.MAX_ERROR_LENGTH);
        assertThat(analysis.getMatchScore()).isNull();
        assertThat(analysis.getCompletedAt()).isNotNull();
    }

    @Test
    void failWithBlankMessageUsesDefault() {
        analysis.fail(" ");
        assertThat(analysis.getErrorMessage()).isEqualTo("Analysis failed");
    }

    @Test
    void terminalStatesAreFinal() {
        analysis.markProcessing();
        analysis.complete(RESULT);
        assertThatThrownBy(() -> analysis.fail("late")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(analysis::markProcessing).isInstanceOf(IllegalStateException.class);
    }
}
