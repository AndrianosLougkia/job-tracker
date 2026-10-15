package com.jobtracker.analysis;

import com.jobtracker.analysis.domain.Analysis;
import com.jobtracker.analysis.domain.AnalysisQuota;
import com.jobtracker.analysis.domain.AnalysisRepository;
import com.jobtracker.infrastructure.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisQuotaTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock AnalysisRepository repository;

    @Test
    void allowsWhileUnderTheLimit() {
        when(repository.countByUserIdAndCreatedAtAfter(1L, NOW.minus(Duration.ofHours(24)))).thenReturn(4L);

        assertThatCode(() -> new AnalysisQuota(repository, 5, CLOCK).checkAllowed(1L)).doesNotThrowAnyException();
    }

    @Test
    void refusesAtTheLimitUntilTheOldestAnalysisLeavesTheWindow() {
        when(repository.countByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(5L);
        Analysis oldest = mock(Analysis.class);
        when(oldest.getCreatedAt()).thenReturn(NOW.minus(Duration.ofHours(21)));
        when(repository.findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(eq(1L), any()))
            .thenReturn(Optional.of(oldest));

        assertThatThrownBy(() -> new AnalysisQuota(repository, 5, CLOCK).checkAllowed(1L))
            .isInstanceOf(TooManyRequestsException.class)
            .hasMessage("Daily limit of 5 analyses reached; try again in 3 hours")
            .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfter())
                .isEqualTo(Duration.ofHours(3)));
    }

    @Test
    void zeroMeansUnlimited() {
        new AnalysisQuota(repository, 0, CLOCK).checkAllowed(1L);

        verifyNoInteractions(repository);
    }
}
