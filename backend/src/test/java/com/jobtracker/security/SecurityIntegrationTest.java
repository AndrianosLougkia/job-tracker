package com.jobtracker.security;

import com.jobtracker.AbstractIntegrationTest;
import com.jobtracker.TestJwtHelper;
import com.jobtracker.application.domain.ApplicationRepository;
import com.jobtracker.application.domain.JobApplication;
import com.jobtracker.auth.infrastructure.JwtService;
import com.jobtracker.resume.domain.Resume;
import com.jobtracker.resume.domain.ResumeRepository;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Authentication, authorisation and user isolation across the whole API, plus the
 * structured error body for requests Spring MVC itself rejects.
 */
@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ResumeRepository resumeRepository;
    @Autowired TestJwtHelper jwtHelper;
    @Value("${app.jwt.secret}") String jwtSecret;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        resumeRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        alice = userRepository.save(new User("alice-" + UUID.randomUUID() + "@example.com", "hash"));
        bob = userRepository.save(new User("bob-" + UUID.randomUUID() + "@example.com", "hash"));
    }

    // ------------------------------------------------------------------ //
    //  Authentication                                                     //
    // ------------------------------------------------------------------ //

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,    /applications",
        "POST,   /applications",
        "GET,    /applications/1",
        "PATCH,  /applications/1",
        "DELETE, /applications/1",
        "GET,    /resumes",
        "POST,   /resumes",
        "GET,    /resumes/1",
        "DELETE, /resumes/1",
        "GET,    /analyses",
        "POST,   /analyses",
        "GET,    /analyses/1",
        "GET,    /dashboard/stats",
        "GET,    /actuator/env",
        "GET,    /no-such-endpoint",
    })
    void everyNonPublicEndpointRequiresAToken(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"/health", "/actuator/health", "/v3/api-docs", "/swagger-ui/index.html"})
    void publicEndpointsNeedNoToken(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expired = new JwtService(jwtSecret, -1_000).generateToken(alice.getId(), alice.getEmail());

        mockMvc.perform(get("/applications").header("Authorization", "Bearer " + expired))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        String forged = new JwtService("x".repeat(64), 60_000).generateToken(alice.getId(), alice.getEmail());

        mockMvc.perform(get("/applications").header("Authorization", "Bearer " + forged))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeletedUserIsRejected() throws Exception {
        String header = jwtHelper.authHeader(alice);
        userRepository.delete(alice);

        mockMvc.perform(get("/applications").header("Authorization", header))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void healthShowsComponentStatusesButNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.db.status").value("UP"))
            .andExpect(jsonPath("$.components.db.details").doesNotExist());
    }

    // ------------------------------------------------------------------ //
    //  User isolation                                                     //
    // ------------------------------------------------------------------ //

    @Test
    void resumesOfOtherUsersAreInvisible() throws Exception {
        Resume bobsResume = resumeRepository.save(
            new Resume(bob, "bob.pdf", "resumes/" + bob.getId() + "/" + UUID.randomUUID() + ".pdf", 10L, "text"));
        String asAlice = jwtHelper.authHeader(alice);

        mockMvc.perform(get("/resumes").header("Authorization", asAlice))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/resumes/" + bobsResume.getId()).header("Authorization", asAlice))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/resumes/" + bobsResume.getId()).header("Authorization", asAlice))
            .andExpect(status().isNotFound());

        assertThat(resumeRepository.findById(bobsResume.getId())).isPresent();
    }

    @Test
    void applicationsOfOtherUsersCannotBeChangedOrDeleted() throws Exception {
        JobApplication bobsApp = applicationRepository.save(new JobApplication(bob, "Bob Corp", "Dev"));
        String asAlice = jwtHelper.authHeader(alice);

        mockMvc.perform(patch("/applications/" + bobsApp.getId()).header("Authorization", asAlice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"Hijacked\"}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/applications/" + bobsApp.getId()).header("Authorization", asAlice))
            .andExpect(status().isNotFound());

        assertThat(applicationRepository.findById(bobsApp.getId()))
            .hasValueSatisfying(app -> assertThat(app.getCompany()).isEqualTo("Bob Corp"));
    }

    @Test
    void dashboardCountsOnlyTheCallersApplications() throws Exception {
        applicationRepository.save(new JobApplication(bob, "Bob Corp", "Dev"));

        mockMvc.perform(get("/dashboard/stats").header("Authorization", jwtHelper.authHeader(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalApplications").value(0))
            .andExpect(jsonPath("$.recentApplications", hasSize(0)));
    }

    // ------------------------------------------------------------------ //
    //  Structured errors for requests rejected by Spring MVC              //
    // ------------------------------------------------------------------ //

    @Test
    void unknownEndpointReturns404WithErrorBody() throws Exception {
        mockMvc.perform(get("/no-such-endpoint").header("Authorization", jwtHelper.authHeader(alice)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value("No endpoint GET /no-such-endpoint"))
            .andExpect(jsonPath("$.path").value("/no-such-endpoint"));
    }

    @Test
    void unsupportedMethodReturns405WithErrorBody() throws Exception {
        mockMvc.perform(put("/applications").header("Authorization", jwtHelper.authHeader(alice)))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().exists("Allow"))
            .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void uploadWithoutFilePartReturns400() throws Exception {
        mockMvc.perform(multipart("/resumes").file(new MockMultipartFile("other", new byte[] {1}))
                .header("Authorization", jwtHelper.authHeader(alice)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void uploadWhileStorageIsDownReturns503AndStoresNothing() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4".getBytes());

        mockMvc.perform(multipart("/resumes").file(pdf).header("Authorization", jwtHelper.authHeader(alice)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value("Storage service is unavailable"));

        assertThat(resumeRepository.findByUserIdOrderByCreatedAtDesc(alice.getId())).isEmpty();
    }
}
