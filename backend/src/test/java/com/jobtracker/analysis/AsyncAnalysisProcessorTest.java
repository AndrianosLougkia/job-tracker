package com.jobtracker.analysis;

import com.jobtracker.ai.domain.AiProvider;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.domain.AiProviderFactory;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.infrastructure.MockAiProvider;
import com.jobtracker.analysis.domain.Analysis;
import com.jobtracker.analysis.domain.AnalysisRepository;
import com.jobtracker.analysis.domain.AnalysisStatus;
import com.jobtracker.analysis.infrastructure.AsyncAnalysisProcessor;
import com.jobtracker.notification.domain.AnalysisNotification;
import com.jobtracker.notification.domain.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The background half of an analysis, called directly (no executor) so each
 * state transition and notification can be checked deterministically.
 */
@ExtendWith(MockitoExtension.class)
class AsyncAnalysisProcessorTest {

    private static final long USER = 1L, APP = 10L, RESUME = 20L, ID = 100L;
    private static final AnalysisRequest REQUEST = new AnalysisRequest(
        "Java developer with Docker experience.", "Looking for Java, Docker and Kubernetes skills.");

    @Mock AnalysisRepository analysisRepository;
    @Mock NotificationService notificationService;

    private AiProvider provider;
    private AsyncAnalysisProcessor processor;
    private Analysis analysis;
    private final List<AnalysisStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        provider = spy(new MockAiProvider());
        processor = new AsyncAnalysisProcessor(analysisRepository,
            new AiProviderFactory(List.of(provider), provider.name()), notificationService);
        analysis = new Analysis(USER, APP, RESUME, "mock");
        setId(analysis, ID);
    }

    private void givenStoredAnalysis() {
        when(analysisRepository.findById(ID)).thenReturn(Optional.of(analysis));
        when(analysisRepository.save(any())).thenAnswer(inv -> {
            Analysis a = inv.getArgument(0);
            savedStatuses.add(a.getStatus());
            return a;
        });
    }

    @Test
    void successGoesThroughProcessingToCompletedAndNotifiesOwner() {
        givenStoredAnalysis();

        processor.process(ID, REQUEST);

        assertThat(savedStatuses).containsExactly(AnalysisStatus.PROCESSING, AnalysisStatus.COMPLETED);
        assertThat(analysis.getMatchScore()).isEqualTo(67);
        assertThat(analysis.getResult().missingSkills()).containsExactly("Kubernetes");
        assertThat(analysis.getCompletedAt()).isNotNull();

        AnalysisNotification sent = captureNotification();
        assertThat(sent).isEqualTo(new AnalysisNotification(ID, APP, "COMPLETED", 67, "Analysis complete: 67% match"));
    }

    @Test
    void providerIsCalledOnlyAfterProcessingIsSaved() {
        givenStoredAnalysis();

        processor.process(ID, REQUEST);

        InOrder order = inOrder(analysisRepository, provider, notificationService);
        order.verify(analysisRepository).save(analysis);
        order.verify(provider).analyze(REQUEST);
        order.verify(analysisRepository).save(analysis);
        order.verify(notificationService).notifyAnalysisFinished(eq(USER), any());
    }

    @Test
    void providerFailureIsPersistedAsFailedAndNotified() {
        givenStoredAnalysis();
        doThrow(new AiProviderException("Ollama is not reachable at http://localhost:11434; is it running?"))
            .when(provider).analyze(any());

        processor.process(ID, REQUEST);

        assertThat(savedStatuses).containsExactly(AnalysisStatus.PROCESSING, AnalysisStatus.FAILED);
        assertThat(analysis.getErrorMessage()).isEqualTo("Ollama is not reachable at http://localhost:11434; is it running?");
        assertThat(analysis.getResult()).isNull();
        assertThat(analysis.getMatchScore()).isNull();

        AnalysisNotification sent = captureNotification();
        assertThat(sent.status()).isEqualTo("FAILED");
        assertThat(sent.matchScore()).isNull();
        assertThat(sent.message()).startsWith("Analysis failed: Ollama is not reachable");
    }

    @Test
    void unexpectedProviderErrorIsSanitised() {
        givenStoredAnalysis();
        doThrow(new NullPointerException("internal detail: apiKey=sk-123")).when(provider).analyze(any());

        processor.process(ID, REQUEST);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getErrorMessage()).isEqualTo("Unexpected error while running AI analysis");
        assertThat(captureNotification().message()).doesNotContain("sk-123");
    }

    @Test
    void deletedAnalysisIsSkipped() {
        when(analysisRepository.findById(ID)).thenReturn(Optional.empty());

        processor.process(ID, REQUEST);

        verify(provider, never()).analyze(any());
        verify(analysisRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void persistenceFailureDoesNotEscapeOrNotify() {
        when(analysisRepository.findById(ID)).thenReturn(Optional.of(analysis));
        when(analysisRepository.save(any()))
            .thenReturn(analysis)
            .thenThrow(new DataIntegrityViolationException("application was deleted"));

        processor.process(ID, REQUEST);

        verifyNoInteractions(notificationService);
    }

    @Test
    void analysisThatIsNoLongerPendingIsNotRunAgain() {
        analysis.markProcessing();
        analysis.fail("Interrupted by a server restart; please run the analysis again");
        when(analysisRepository.findById(ID)).thenReturn(Optional.of(analysis));

        processor.process(ID, REQUEST);

        verify(provider, never()).analyze(any());
        verifyNoInteractions(notificationService);
    }

    private AnalysisNotification captureNotification() {
        ArgumentCaptor<AnalysisNotification> captor = ArgumentCaptor.forClass(AnalysisNotification.class);
        verify(notificationService).notifyAnalysisFinished(eq(USER), captor.capture());
        return captor.getValue();
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
