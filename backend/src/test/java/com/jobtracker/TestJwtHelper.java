package com.jobtracker;

import com.jobtracker.auth.infrastructure.JwtService;
import com.jobtracker.user.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestJwtHelper {

    @Autowired private JwtService jwtService;

    public String tokenFor(User user) {
        return jwtService.generateToken(user.getId(), user.getEmail());
    }

    public String authHeader(User user) {
        return "Bearer " + tokenFor(user);
    }
}
