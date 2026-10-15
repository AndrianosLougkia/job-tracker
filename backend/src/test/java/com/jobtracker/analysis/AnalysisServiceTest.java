package com.jobtracker.analysis;

import com.jobtracker.ai.domain.AiProvider;
import com.jobtracker.ai.domain.AiProviderFactory;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.infrastructure.MockAiProvider;
import com.jobtracker.analysis.api.AnalysisResponse;
import com.jobtracker.analysis.domain.*;
import com.jobtracker.analysis.infrastructure.AsyncAnalysisProcessor;
import com.jobtracker.application.domain.ApplicationService;
import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import com.jobtracker.infrastructure.exception.TooManyRequestsException;
import com.jobtracker.resume.domain.ResumeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    private static final long USER = 1L, APP = 10L, RESUME = 20L;
    private static final String JOB = "Looking for Java, Docker and Kubernetes skills.";
    private static final String RESUME_TEXT = "Java developer with Docker experience.";

    @Mock AnalysisRepository analysisRepository;
    @Mock ApplicationService applicationService;
    @Mock ResumeService resumeService;
    @Mock AsyncAnalysisProcessor processor;
    @Mock AnalysisQuota quota;

    private AiProvider provider;
    private AnalysisService service;
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        provider = spy(new MockAiProvider());
        service = newService(provider, 50_000);
    }

    private AnalysisService newService(AiProvider aiProvider, int maxChars) {
        return new AnalysisService(analysisRepository, applicationService, resumeService,
            new AiProviderFactory(List.of(aiProvider), aiProvider.name()), processor, quota, maxChars);
    }

    private void givenInputs(String job, String resumeText) {
        when(applicationService.getJobDescription(APP, USER)).thenReturn(job);
        when(resumeService.getExtractedText(RESUME, USER)).thenReturn(resumeText);
    }

    /** Emulates the database assigning an ID on first save. */
    private void givenSaveAssignsIds() {
        when(analysisRepository.save(any())).thenAnswer(inv -> {
            Analysis a = inv.getArgument(0);
            if (a.getId() == null) setId(a, ids.getAndIncrement());
            return a;
        });
    }

    @Test
    void requestPersistsPendingAnalysisAndHandsItToTheBackgroundProcessor() {
        givenInputs(JOB, RESUME_TEXT);
        givenSaveAssignsIds();

        AnalysisResponse response = service.requestAnalysis(APP, RESUME, USER);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(response.getProvider()).isEqualTo("mock");
        assertThat(response.getMatchScore()).isNull();

        ArgumentCaptor<Analysis> saved = ArgumentCaptor.forClass(Analysis.class);
        verify(analysisRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        assertThat(saved.getValue().getApplicationId()).isEqualTo(APP);
        assertThat(saved.getValue().getResumeId()).isEqualTo(RESUME);

        ArgumentCaptor<AnalysisRequest> request = ArgumentCaptor.forClass(AnalysisRequest.class);
        verify(processor).process(eq(100L), request.capture());
        assertThat(request.getValue().resumeText()).isEqualTo(RESUME_TEXT);
        assertThat(request.getValue().jobDescription()).isEqualTo(JOB);
        // The AI call happens in the background, never in the request.
        verify(provider, never()).analyze(any());
    }

    @Test
    void fullQueueStoresAnalysisAsFailed() {
        givenInputs(JOB, RESUME_TEXT);
        givenSaveAssignsIds();
        doThrow(new TaskRejectedException("queue full")).when(processor).process(any(), any());

        AnalysisResponse response = service.requestAnalysis(APP, RESUME, USER);

        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(response.getErrorMessage()).contains("try again");
        verify(analysisRepository, times(2)).save(any());
    }

    @Test
    void missingJobDescriptionIsRejectedBeforeCallingProvider() {
        givenInputs("   ", RESUME_TEXT);

        assertThatThrownBy(() -> service.requestAnalysis(APP, RESUME, USER))
            .isInstanceOf(AnalysisInputException.class)
            .hasMessageContaining("no job description");
        verifyNoInteractions(analysisRepository, processor);
    }

    @Test
    void resumeWithoutExtractedTextIsRejected() {
        givenInputs(JOB, null);

        assertThatThrownBy(() -> service.requestAnalysis(APP, RESUME, USER))
            .isInstanceOf(AnalysisInputException.class)
            .hasMessageContaining("no extracted text");
        verifyNoInteractions(analysisRepository);
    }

    @Test
    void oversizedInputIsRejected() {
        service = newService(provider, 20);
        givenInputs(JOB, RESUME_TEXT);

        assertThatThrownBy(() -> service.requestAnalysis(APP, RESUME, USER))
            .isInstanceOf(AnalysisInputException.class)
            .hasMessageContaining("exceeds the maximum of 20 characters");
        verifyNoInteractions(analysisRepository);
    }

    @Test
    void userOverTheDailyLimitIsRejectedBeforeAnythingIsStored() {
        givenInputs(JOB, RESUME_TEXT);
        doThrow(new TooManyRequestsException("Daily limit of 20 analyses reached", Duration.ofHours(3)))
            .when(quota).checkAllowed(USER);

        assertThatThrownBy(() -> service.requestAnalysis(APP, RESUME, USER))
            .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(analysisRepository, processor);
    }

    @Test
    void foreignApplicationPropagatesNotFound() {
        when(applicationService.getJobDescription(APP, USER))
            .thenThrow(ResourceNotFoundException.application(APP));

        assertThatThrownBy(() -> service.requestAnalysis(APP, RESUME, USER))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(resumeService, analysisRepository, processor);
    }

    @Test
    void getAnalysisEnforcesOwnership() {
        when(analysisRepository.findByIdAndUserId(5L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnalysis(5L, USER))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Analysis not found");
    }

    @Test
    void listFiltersByApplicationWhenGiven() {
        when(analysisRepository.findByUserIdAndApplicationIdOrderByCreatedAtDescIdDesc(USER, APP))
            .thenReturn(List.of(new Analysis(USER, APP, RESUME, "mock")));

        assertThat(service.listAnalyses(USER, APP)).hasSize(1);
        verify(analysisRepository, never()).findByUserIdOrderByCreatedAtDescIdDesc(any());
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
