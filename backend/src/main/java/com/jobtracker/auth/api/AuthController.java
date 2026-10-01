package com.jobtracker.auth.api;

import com.jobtracker.auth.domain.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication endpoints.
 * These routes are explicitly permitted in SecurityConfig — no JWT required.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Registration and login")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<AuthDtos.RegisterResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT")
    public ResponseEntity<AuthDtos.LoginResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
