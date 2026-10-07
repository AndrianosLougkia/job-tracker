package com.jobtracker.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public static class RegisterRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        private String email;
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;
        public String getEmail()    { return email; }
        public String getPassword() { return password; }
        public void setEmail(String e)    { this.email = e; }
        public void setPassword(String p) { this.password = p; }
    }

    public static class RegisterResponse {
        private final Long id;
        private final String email;
        public RegisterResponse(Long id, String email) { this.id = id; this.email = email; }
        public Long getId()      { return id; }
        public String getEmail() { return email; }
    }

    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        private String email;
        @NotBlank(message = "Password is required")
        private String password;
        public String getEmail()    { return email; }
        public String getPassword() { return password; }
        public void setEmail(String e)    { this.email = e; }
        public void setPassword(String p) { this.password = p; }
    }

    public static class LoginResponse {
        private final String token;
        private final Long userId;
        private final String email;
        public LoginResponse(String token, Long userId, String email) {
            this.token = token; this.userId = userId; this.email = email;
        }
        public String getToken()  { return token; }
        public Long getUserId()   { return userId; }
        public String getEmail()  { return email; }
    }
}
