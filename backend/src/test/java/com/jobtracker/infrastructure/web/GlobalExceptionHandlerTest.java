package com.jobtracker.infrastructure.web;

import com.jobtracker.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for GlobalExceptionHandler — no Spring context needed.
 */
class GlobalExceptionHandlerTest {

    @Test
    void errorBodyContainsRequiredFields() {
        Map<String, Object> body = GlobalExceptionHandler.errorBody(
            404, "Not Found", "Resource missing", "/api/things/99");

        assertThat(body).containsKey("timestamp");
        assertThat(body).containsEntry("status", 404);
        assertThat(body).containsEntry("error", "Not Found");
        assertThat(body).containsEntry("message", "Resource missing");
        assertThat(body).containsEntry("path", "/api/things/99");
    }
}
