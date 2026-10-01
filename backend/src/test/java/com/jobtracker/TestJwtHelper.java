package com.jobtracker;

import com.jobtracker.auth.infrastructure.JwtService;
import com.jobtracker.user.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Generates real JWT tokens for use in integration tests.
 * Uses the same JwtService bean as the application so tokens are always valid.
 */
@Component
public class TestJwtHelper {

    @Autowired
    private JwtService jwtService;

    public String tokenFor(User user) {
        return jwtService.generateToken(user.getId(), user.getEmail());
    }

    public String authHeader(User user) {
        return "Bearer " + tokenFor(user);
    }
}
