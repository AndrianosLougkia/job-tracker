package com.jobtracker.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.AbstractRedisIntegrationTest;
import com.jobtracker.TestJwtHelper;
import com.jobtracker.application.domain.ApplicationRepository;
import com.jobtracker.application.domain.ApplicationService;
import com.jobtracker.application.domain.ApplicationStatus;
import com.jobtracker.application.domain.JobApplication;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard statistics against real PostgreSQL and Redis: cache hits and misses, TTL,
 * invalidation after each kind of write, and isolation between users.
 */
@AutoConfigureMockMvc
class DashboardCacheIntegrationTest extends AbstractRedisIntegrationTest {

    private static final String KEY_PREFIX = "jobtracker:dashboard:stats:";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StringRedisTemplate redis;
    @Autowired TestJwtHelper jwtHelper;
    @Autowired UserRepository userRepository;
    @Autowired ApplicationRepository applicationRepository;
    @SpyBean ApplicationService applicationService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();

        alice = userRepository.save(new User("alice@example.com", "hash"));
        bob = userRepository.save(new User("bob@example.com", "hash"));
        clearInvocations(applicationService);
    }

    // ------------------------------------------------------------------ //
    //  Hits, misses, TTL                                                  //
    // ------------------------------------------------------------------ //

    @Test
    void firstRequestIsComputedAndSecondIsServedFromRedis() throws Exception {
        save(alice, "Acme", ApplicationStatus.APPLIED);

        JsonNode first = stats(alice);
        JsonNode second = stats(alice);

        verify(applicationService, times(1)).getStatistics(alice.getId());
        assertThat(second).isEqualTo(first);
        assertThat(second.get("generatedAt").asText()).isEqualTo(first.get("generatedAt").asText());
        assertThat(redis.hasKey(KEY_PREFIX + alice.getId())).isTrue();
    }

    @Test
    void entriesExpireWithinTheConfiguredTtl() throws Exception {
        stats(alice);

        Long ttlSeconds = redis.getExpire(KEY_PREFIX + alice.getId());
        assertThat(ttlSeconds).isBetween(1L, 300L);
    }

    @Test
    void cachedValueIsPlainJsonWithoutClassNames() throws Exception {
        save(alice, "Acme", ApplicationStatus.APPLIED);
        stats(alice);

        String stored = redis.opsForValue().get(KEY_PREFIX + alice.getId());
        assertThat(stored).contains("\"totalApplications\":1").doesNotContain("@class");
    }

    @Test
    void statisticsAreCorrect() throws Exception {
        save(alice, "A", ApplicationStatus.WISHLIST);
        save(alice, "B", ApplicationStatus.APPLIED);
        save(alice, "C", ApplicationStatus.APPLIED);
        save(alice, "D", ApplicationStatus.INTERVIEW);
        save(alice, "E", ApplicationStatus.OFFER);
        save(alice, "F", ApplicationStatus.REJECTED);

        JsonNode stats = stats(alice);

        assertThat(stats.get("totalApplications").asLong()).isEqualTo(6);
        assertThat(stats.get("interviewCount").asLong()).isEqualTo(1);
        assertThat(stats.get("offerCount").asLong()).isEqualTo(1);
        assertThat(stats.get("byStatus").get("APPLIED").asLong()).isEqualTo(2);
        assertThat(stats.get("byStatus").get("WITHDRAWN").asLong()).isZero();
        assertThat(stats.get("byStatus").size()).isEqualTo(ApplicationStatus.values().length);
        JsonNode recent = stats.get("recentApplications");
        assertThat(recent).hasSize(5);
        assertThat(recent.get(0).get("company").asText()).isEqualTo("F");
        assertThat(recent.get(4).get("company").asText()).isEqualTo("B");
        assertThat(recent.get(0).has("jobDescription")).isFalse();
    }

    // ------------------------------------------------------------------ //
    //  Invalidation                                                       //
    // ------------------------------------------------------------------ //

    @Test
    void creatingAnApplicationEvictsTheOwnersEntry() throws Exception {
        assertThat(stats(alice).get("totalApplications").asLong()).isZero();

        mockMvc.perform(post("/applications").header("Authorization", jwtHelper.authHeader(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\": \"Acme\", \"role\": \"Engineer\"}"))
            .andExpect(status().isCreated());

        assertThat(redis.hasKey(KEY_PREFIX + alice.getId())).isFalse();
        assertThat(stats(alice).get("totalApplications").asLong()).isEqualTo(1);
    }

    @Test
    void changingStatusEvictsTheOwnersEntry() throws Exception {
        Long id = save(alice, "Acme", ApplicationStatus.APPLIED).getId();
        assertThat(stats(alice).get("interviewCount").asLong()).isZero();

        mockMvc.perform(patch("/applications/" + id).header("Authorization", jwtHelper.authHeader(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"INTERVIEW\"}"))
            .andExpect(status().isOk());

        JsonNode stats = stats(alice);
        assertThat(stats.get("interviewCount").asLong()).isEqualTo(1);
        assertThat(stats.get("byStatus").get("APPLIED").asLong()).isZero();
    }

    @Test
    void deletingAnApplicationEvictsTheOwnersEntry() throws Exception {
        Long id = save(alice, "Acme", ApplicationStatus.APPLIED).getId();
        assertThat(stats(alice).get("totalApplications").asLong()).isEqualTo(1);

        mockMvc.perform(delete("/applications/" + id).header("Authorization", jwtHelper.authHeader(alice)))
            .andExpect(status().isNoContent());

        assertThat(stats(alice).get("totalApplications").asLong()).isZero();
    }

    @Test
    void failedWriteKeepsTheCachedEntry() throws Exception {
        stats(alice);

        mockMvc.perform(patch("/applications/999999").header("Authorization", jwtHelper.authHeader(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"OFFER\"}"))
            .andExpect(status().isNotFound());

        assertThat(redis.hasKey(KEY_PREFIX + alice.getId())).isTrue();
    }

    // ------------------------------------------------------------------ //
    //  User isolation                                                     //
    // ------------------------------------------------------------------ //

    @Test
    void eachUserGetsTheirOwnEntry() throws Exception {
        save(alice, "Alice Corp", ApplicationStatus.OFFER);
        save(bob, "Bob Inc", ApplicationStatus.APPLIED);
        save(bob, "Bob Ltd", ApplicationStatus.APPLIED);

        JsonNode aliceStats = stats(alice);
        JsonNode bobStats = stats(bob);

        assertThat(aliceStats.get("totalApplications").asLong()).isEqualTo(1);
        assertThat(aliceStats.get("recentApplications").get(0).get("company").asText()).isEqualTo("Alice Corp");
        assertThat(bobStats.get("totalApplications").asLong()).isEqualTo(2);
        assertThat(bobStats.get("offerCount").asLong()).isZero();
        assertThat(redis.hasKey(KEY_PREFIX + alice.getId())).isTrue();
        assertThat(redis.hasKey(KEY_PREFIX + bob.getId())).isTrue();
        verify(applicationService, times(2)).getStatistics(anyLong());
    }

    @Test
    void anotherUsersWriteDoesNotEvictYourEntry() throws Exception {
        stats(alice);

        mockMvc.perform(post("/applications").header("Authorization", jwtHelper.authHeader(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\": \"Bob Inc\", \"role\": \"Engineer\"}"))
            .andExpect(status().isCreated());

        assertThat(redis.hasKey(KEY_PREFIX + alice.getId())).isTrue();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard/stats")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ //

    private JsonNode stats(User user) throws Exception {
        String json = mockMvc.perform(get("/dashboard/stats").header("Authorization", jwtHelper.authHeader(user)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JobApplication save(User owner, String company, ApplicationStatus status) {
        JobApplication app = new JobApplication(owner, company, "Engineer");
        app.setStatus(status);
        return applicationRepository.save(app);
    }
}
