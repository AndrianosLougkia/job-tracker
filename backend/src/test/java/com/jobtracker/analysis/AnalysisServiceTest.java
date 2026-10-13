package com.jobtracker.analysis;

import com.jobtracker.ai.domain.AiProvider;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.domain.AiProviderFactory;
import com.jobtracker.ai.infrastructure.MockAiProvider;
import com.jobtracker.analysis.api.AnalysisResponse;
import com.jobtracker.analysis.domain.*;
import com.jobtracker.application.domain.ApplicationService;
import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import com.jobtracker.resume.domain.ResumeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    private static final long USER = 1L, APP = 10L, RESUME = 20L;
    private static final String JOB = "Looking for Java, Docker and Kubernetes skills.";
    private static final String RESUME_TEXT = "Java developer with Docker experience.";

    @Mock AnalysisRepository analysisRepository;
    @Mock ApplicationService applicationService;
    @Mock ResumeService resumeService;

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
            new AiProviderFactory(List.of(aiProvider), aiProvider.name()), maxChars);
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
    void analyzePersistsCompletedAnalysisWithProviderResult() {
        givenInputs(JOB, RESUME_TEXT);
        givenSaveAssignsIds();

        AnalysisResponse response = service.analyze(APP, RESUME, USER);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(response.getProvider()).isEqualTo("mock");
        assertThat(response.getMatchScore()).isEqualTo(67);
        assertThat(response.getMatchingSkills()).containsExactly("Java", "Docker");
        assertThat(response.getMissingSkills()).containsExactly("Kubernetes");
        assertThat(response.getSummary()).isNotBlank();
        assertThat(response.getErrorMessage()).isNull();

        ArgumentCaptor<Analysis> saved = ArgumentCaptor.forClass(Analysis.class);
        verify(analysisRepository, times(2)).save(saved.capture());
        Analysis analysis = saved.getValue();
        assertThat(analysis.getUserId()).isEqualTo(USER);
        assertThat(analysis.getApplicationId()).isEqualTo(APP);
        assertThat(analysis.getResumeId()).isEqualTo(RESUME);
    }

    @Test
    void missingJobDescriptionIsRejectedBeforeCallingProvider() {
        givenInputs("   ", RESUME_TEXT);

        assertThatThrownBy(() -> service.analyze(APP, RESUME, USER))
            .isInstanceOf(AnalysisInputException.class)
            .hasMessageContaining("no job description");
        verifyNoInteractions(analysisRepository);
        verify(provider, never()).analyze(any());
    }

    @Test
    void resumeWithoutExtractedTextIsRejected() {
        givenInputs(JOB, null);

        assertThatThrownBy(() -> service.analyze(APP, RESUME, USER))
            .isInstanceOf(AnalysisInputException.class)
            .hasMessageContaining("no extracted text");
        verifyNoInteractions(analysisRepository);
    }

    @Test
    void oversizedInputIsRejected() {
        service = newService(provider, 20);
        givenInputs(JOB, RESUME_TEXT);

        assertThatThrownBy(() -> service.analyze(APP, RESUME, USER))
            .isInstanceOf(AnalysisInputException.class)
            .hasMessageContaining("exceeds the maximum of 20 characters");
        verifyNoInteractions(analysisRepository);
    }

    @Test
    void foreignApplicationPropagatesNotFound() {
        when(applicationService.getJobDescription(APP, USER))
            .thenThrow(ResourceNotFoundException.application(APP));

        assertThatThrownBy(() -> service.analyze(APP, RESUME, USER))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(resumeService, analysisRepository);
    }

    @Test
    void providerFailureIsPersistedAsFailedThenReported() {
        givenInputs(JOB, RESUME_TEXT);
        givenSaveAssignsIds();
        doThrow(new AiProviderException("AI provider timed out")).when(provider).analyze(any());

        assertThatThrownBy(() -> service.analyze(APP, RESUME, USER))
            .isInstanceOf(AnalysisFailedException.class)
            .hasMessage("AI analysis failed: AI provider timed out")
            .extracting(e -> ((AnalysisFailedException) e).getAnalysisId()).isEqualTo(100L);

        ArgumentCaptor<Analysis> saved = ArgumentCaptor.forClass(Analysis.class);
        verify(analysisRepository, times(2)).save(saved.capture());
        Analysis failed = saved.getValue();
        assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("AI provider timed out");
        assertThat(failed.getResult()).isNull();
    }

    @Test
    void unexpectedProviderErrorIsSanitised() {
        givenInputs(JOB, RESUME_TEXT);
        givenSaveAssignsIds();
        doThrow(new NullPointerException("internal detail: apiKey=sk-123")).when(provider).analyze(any());

        assertThatThrownBy(() -> service.analyze(APP, RESUME, USER))
            .isInstanceOf(AnalysisFailedException.class)
            .message().doesNotContain("sk-123");

        ArgumentCaptor<Analysis> saved = ArgumentCaptor.forClass(Analysis.class);
        verify(analysisRepository, times(2)).save(saved.capture());
        assertThat(saved.getValue().getErrorMessage())
            .isEqualTo("Unexpected error while running AI analysis");
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
