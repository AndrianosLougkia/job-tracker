package com.jobtracker.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.AbstractIntegrationTest;
import com.jobtracker.TestJwtHelper;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.infrastructure.MockAiProvider;
import com.jobtracker.analysis.api.TriggerAnalysisRequest;
import com.jobtracker.analysis.domain.Analysis;
import com.jobtracker.analysis.domain.AnalysisRepository;
import com.jobtracker.analysis.domain.AnalysisStatus;
import com.jobtracker.application.domain.ApplicationRepository;
import com.jobtracker.application.domain.JobApplication;
import com.jobtracker.resume.domain.Resume;
import com.jobtracker.resume.domain.ResumeRepository;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end analysis flow against PostgreSQL, using the deterministic Mock provider.
 */
@AutoConfigureMockMvc
class AnalysisControllerTest extends AbstractIntegrationTest {

    private static final String JOB = "We need Java, Spring Boot, Kubernetes, PostgreSQL and AWS experience.";
    private static final String RESUME_TEXT =
        "Backend engineer. 5 years of Java and Spring Boot, PostgreSQL, Docker and REST APIs.";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ResumeRepository resumeRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestJwtHelper jwtHelper;
    @SpyBean MockAiProvider mockAiProvider;

    private User user;
    private String authHeader;
    private Long applicationId;
    private Long resumeId;

    @BeforeEach
    void setUp() {
        analysisRepository.deleteAll();
        resumeRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(new User("analyst@example.com", "hash"));
        authHeader = jwtHelper.authHeader(user);
        applicationId = saveApplication(user, JOB).getId();
        resumeId = saveResume(user, RESUME_TEXT).getId();
    }

    // ------------------------------------------------------------------ //
    //  Happy path                                                         //
    // ------------------------------------------------------------------ //

    @Test
    void createAnalysisReturnsMockResultAndPersistsIt() throws Exception {
        String json = trigger(applicationId, resumeId, authHeader)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.applicationId").value(applicationId))
            .andExpect(jsonPath("$.resumeId").value(resumeId))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.provider").value("mock"))
            .andExpect(jsonPath("$.matchScore").value(60))
            .andExpect(jsonPath("$.matchingSkills", contains("Java", "Spring Boot", "PostgreSQL")))
            .andExpect(jsonPath("$.missingSkills", contains("Kubernetes", "AWS")))
            .andExpect(jsonPath("$.strengths", hasSize(2)))
            .andExpect(jsonPath("$.recommendations", hasSize(2)))
            .andExpect(jsonPath("$.summary").value("Moderate match: the resume covers 3 of 5 required skills (60%)."))
            .andExpect(jsonPath("$.errorMessage").doesNotExist())
            .andExpect(jsonPath("$.completedAt").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(json).get("id").asLong();
        Analysis stored = analysisRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(stored.getUserId()).isEqualTo(user.getId());
        assertThat(stored.getMatchScore()).isEqualTo(60);
        assertThat(stored.getResult().missingSkills()).containsExactly("Kubernetes", "AWS");
    }

    @Test
    void getAnalysisReadsResultBackFromJsonb() throws Exception {
        Long id = createAnalysis();

        mockMvc.perform(get("/analyses/" + id).header("Authorization", authHeader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.matchScore").value(60))
            .andExpect(jsonPath("$.matchingSkills", contains("Java", "Spring Boot", "PostgreSQL")))
            .andExpect(jsonPath("$.summary").isNotEmpty());
    }

    @Test
    void listAnalysesFiltersByApplication() throws Exception {
        createAnalysis();
        Long otherAppId = saveApplication(user, "Python and Django role").getId();
        trigger(otherAppId, resumeId, authHeader).andExpect(status().isCreated());

        mockMvc.perform(get("/analyses").header("Authorization", authHeader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/analyses").param("applicationId", applicationId.toString())
                .header("Authorization", authHeader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].applicationId").value(applicationId));
    }

    @Test
    void deletingApplicationRemovesItsAnalyses() throws Exception {
        createAnalysis();

        mockMvc.perform(delete("/applications/" + applicationId).header("Authorization", authHeader))
            .andExpect(status().isNoContent());

        assertThat(analysisRepository.count()).isZero();
    }

    // ------------------------------------------------------------------ //
    //  Validation                                                         //
    // ------------------------------------------------------------------ //

    @Test
    void missingIdsReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void nonPositiveIdReturns400() throws Exception {
        trigger(0L, resumeId, authHeader)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]", containsString("applicationId")));
    }

    @Test
    void malformedBodyReturns400() throws Exception {
        mockMvc.perform(post("/analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content("{\"applicationId\": \"abc\", \"resumeId\": 1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void invalidQueryParameterReturns400() throws Exception {
        mockMvc.perform(get("/analyses").param("applicationId", "abc")
                .header("Authorization", authHeader))
            .andExpect(status().isBadRequest());
    }

    @Test
    void applicationWithoutJobDescriptionReturns422() throws Exception {
        Long emptyAppId = saveApplication(user, null).getId();

        trigger(emptyAppId, resumeId, authHeader)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message", containsString("no job description")));
        assertThat(analysisRepository.count()).isZero();
    }

    @Test
    void resumeWithoutExtractedTextReturns422() throws Exception {
        Long unreadableResumeId = saveResume(user, null).getId();

        trigger(applicationId, unreadableResumeId, authHeader)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message", containsString("no extracted text")));
    }

    // ------------------------------------------------------------------ //
    //  Security and ownership                                             //
    // ------------------------------------------------------------------ //

    @Test
    void requiresAuthentication() throws Exception {
        trigger(applicationId, resumeId, null).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/analyses")).andExpect(status().isUnauthorized());
    }

    @Test
    void cannotAnalyseAnotherUsersResume() throws Exception {
        User other = userRepository.save(new User("other@example.com", "hash"));
        Long foreignResumeId = saveResume(other, RESUME_TEXT).getId();

        trigger(applicationId, foreignResumeId, authHeader).andExpect(status().isNotFound());
        assertThat(analysisRepository.count()).isZero();
    }

    @Test
    void cannotAnalyseAnotherUsersApplication() throws Exception {
        User other = userRepository.save(new User("other@example.com", "hash"));
        Long foreignAppId = saveApplication(other, JOB).getId();

        trigger(foreignAppId, resumeId, authHeader).andExpect(status().isNotFound());
    }

    @Test
    void cannotReadAnotherUsersAnalysis() throws Exception {
        Long id = createAnalysis();
        User intruder = userRepository.save(new User("intruder@example.com", "hash"));
        String intruderHeader = jwtHelper.authHeader(intruder);

        mockMvc.perform(get("/analyses/" + id).header("Authorization", intruderHeader))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/analyses").header("Authorization", intruderHeader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    // ------------------------------------------------------------------ //
    //  Provider failure                                                   //
    // ------------------------------------------------------------------ //

    @Test
    void providerFailureReturns502AndPersistsFailedAnalysis() throws Exception {
        doThrow(new AiProviderException("AI provider timed out")).when(mockAiProvider).analyze(any());

        String json = trigger(applicationId, resumeId, authHeader)
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("AI analysis failed: AI provider timed out"))
            .andExpect(jsonPath("$.analysisId").isNumber())
            .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(json).get("analysisId").asLong();
        mockMvc.perform(get("/analyses/" + id).header("Authorization", authHeader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorMessage").value("AI provider timed out"))
            .andExpect(jsonPath("$.matchScore").doesNotExist());
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //

    private ResultActions trigger(Long appId, Long resId, String header) throws Exception {
        TriggerAnalysisRequest req = new TriggerAnalysisRequest();
        req.setApplicationId(appId);
        req.setResumeId(resId);
        var builder = post("/analyses")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req));
        if (header != null) builder.header("Authorization", header);
        return mockMvc.perform(builder);
    }

    private Long createAnalysis() throws Exception {
        String json = trigger(applicationId, resumeId, authHeader)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private JobApplication saveApplication(User owner, String jobDescription) {
        JobApplication app = new JobApplication(owner, "Acme Corp", "Backend Engineer");
        app.setJobDescription(jobDescription);
        return applicationRepository.save(app);
    }

    private Resume saveResume(User owner, String extractedText) {
        return resumeRepository.save(new Resume(owner, "cv.pdf",
            "resumes/" + owner.getId() + "/" + UUID.randomUUID() + ".pdf", 1024L, extractedText));
    }
}
