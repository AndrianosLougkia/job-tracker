package com.jobtracker.smoke;

import com.jobtracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests — verify the application context starts and public endpoints respond.
 */
class ApplicationSmokeTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // If the context fails to start the test itself fails to load.
    }

    @Test
    void healthEndpointReturns200() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/health", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void actuatorHealthReturns200() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/actuator/health", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void protectedEndpointReturns401WithNoCredentials() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/applications", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
