package com.jobtracker.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.AbstractIntegrationTest;
import com.jobtracker.TestJwtHelper;
import com.jobtracker.application.domain.ApplicationRepository;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cache is configured for Redis but nothing listens on the Redis port: the
 * dashboard must still answer correctly, quickly, and never serve stale numbers.
 */
@AutoConfigureMockMvc
class DashboardWithoutRedisIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void unreachableRedis(DynamicPropertyRegistry registry) throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        registry.add("spring.cache.type", () -> "redis");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> closedPort);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestJwtHelper jwtHelper;
    @Autowired UserRepository userRepository;
    @Autowired ApplicationRepository applicationRepository;

    private String authHeader;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        authHeader = jwtHelper.authHeader(userRepository.save(new User("noredis@example.com", "hash")));
    }

    @Test
    void dashboardFallsBackToDatabaseAndStaysCurrent() throws Exception {
        assertThat(totalApplications()).isZero();

        mockMvc.perform(post("/applications").header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\": \"Acme\", \"role\": \"Engineer\"}"))
            .andExpect(status().isCreated());

        assertThat(totalApplications()).isEqualTo(1);
    }

    @Test
    void unavailableRedisDoesNotMakeRequestsSlow() throws Exception {
        totalApplications(); // first connection attempt warms up the client
        long start = System.nanoTime();
        totalApplications();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(2_000);
    }

    @Test
    void healthStaysUpWithoutRedis() throws Exception {
        String json = mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(json).get("status").asText()).isEqualTo("UP");
    }

    private long totalApplications() throws Exception {
        String json = mockMvc.perform(get("/dashboard/stats").header("Authorization", authHeader))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("totalApplications").asLong();
    }
}
