package com.jobtracker.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.AbstractIntegrationTest;
import com.jobtracker.TestJwtHelper;
import com.jobtracker.analysis.domain.AnalysisRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The per-user daily analysis limit, end to end against PostgreSQL. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.analysis.daily-limit=2")
class AnalysisQuotaIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ResumeRepository resumeRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestJwtHelper jwtHelper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        analysisRepository.deleteAll();
        resumeRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void thirdAnalysisWithin24HoursIsRefusedWith429() throws Exception {
        Fixture alice = fixture("alice");
        trigger(alice).andExpect(status().isAccepted());
        trigger(alice).andExpect(status().isAccepted());

        trigger(alice)
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", matchesPattern("\\d+")))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.message", startsWith("Daily limit of 2 analyses reached")));
    }

    @Test
    void limitIsPerUser() throws Exception {
        Fixture alice = fixture("alice");
        Fixture bob = fixture("bob");
        trigger(alice).andExpect(status().isAccepted());
        trigger(alice).andExpect(status().isAccepted());

        trigger(bob).andExpect(status().isAccepted());
    }

    @Test
    void analysesOlderThan24HoursNoLongerCount() throws Exception {
        Fixture alice = fixture("alice");
        trigger(alice).andExpect(status().isAccepted());
        trigger(alice).andExpect(status().isAccepted());

        jdbcTemplate.update("UPDATE analyses SET created_at = NOW() - INTERVAL '25 hours' WHERE user_id = ?",
            alice.user.getId());

        trigger(alice).andExpect(status().isAccepted());
    }

    private record Fixture(User user, Long applicationId, Long resumeId) {}

    private Fixture fixture(String name) {
        User user = userRepository.save(new User(name + "-" + UUID.randomUUID() + "@example.com", "hash"));
        JobApplication app = new JobApplication(user, "Acme", "Engineer");
        app.setJobDescription("Java and Spring Boot");
        Long appId = applicationRepository.save(app).getId();
        Long resumeId = resumeRepository.save(new Resume(user, "cv.pdf",
            "resumes/" + user.getId() + "/" + UUID.randomUUID() + ".pdf", 10L, "Java developer")).getId();
        return new Fixture(user, appId, resumeId);
    }

    private ResultActions trigger(Fixture f) throws Exception {
        return mockMvc.perform(post("/analyses")
            .header("Authorization", jwtHelper.authHeader(f.user))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"applicationId\":" + f.applicationId + ",\"resumeId\":" + f.resumeId + "}"));
    }
}
