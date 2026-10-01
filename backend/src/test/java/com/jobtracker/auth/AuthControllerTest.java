package com.jobtracker.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.AbstractIntegrationTest;
import com.jobtracker.auth.api.AuthDtos;
import com.jobtracker.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // ------------------------------------------------------------------ //
    //  Registration                                                       //
    // ------------------------------------------------------------------ //

    @Test
    void registerCreatesUserAndReturns201() throws Exception {
        AuthDtos.RegisterRequest req = new AuthDtos.RegisterRequest();
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        AuthDtos.RegisterRequest req = new AuthDtos.RegisterRequest();
        req.setEmail("dup@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());

        // Second registration with same email
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict());
    }

    @Test
    void registerValidatesEmailFormat() throws Exception {
        AuthDtos.RegisterRequest req = new AuthDtos.RegisterRequest();
        req.setEmail("not-an-email");
        req.setPassword("password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors", hasItem(containsString("email"))));
    }

    @Test
    void registerValidatesPasswordLength() throws Exception {
        AuthDtos.RegisterRequest req = new AuthDtos.RegisterRequest();
        req.setEmail("test@example.com");
        req.setPassword("short");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors", hasItem(containsString("password"))));
    }

    // ------------------------------------------------------------------ //
    //  Login                                                              //
    // ------------------------------------------------------------------ //

    @Test
    void loginReturnsJwtOnValidCredentials() throws Exception {
        // Register first
        AuthDtos.RegisterRequest reg = new AuthDtos.RegisterRequest();
        reg.setEmail("bob@example.com");
        reg.setPassword("securepass");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
            .andExpect(status().isCreated());

        // Login
        AuthDtos.LoginRequest login = new AuthDtos.LoginRequest();
        login.setEmail("bob@example.com");
        login.setPassword("securepass");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.token", not(emptyString())))
            .andExpect(jsonPath("$.userId").isNumber())
            .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    void loginReturns401ForWrongPassword() throws Exception {
        AuthDtos.RegisterRequest reg = new AuthDtos.RegisterRequest();
        reg.setEmail("carol@example.com");
        reg.setPassword("correctpassword");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
            .andExpect(status().isCreated());

        AuthDtos.LoginRequest login = new AuthDtos.LoginRequest();
        login.setEmail("carol@example.com");
        login.setPassword("wrongpassword");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginReturns401ForUnknownEmail() throws Exception {
        AuthDtos.LoginRequest login = new AuthDtos.LoginRequest();
        login.setEmail("nobody@example.com");
        login.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ //
    //  Protected endpoint access                                          //
    // ------------------------------------------------------------------ //

    @Test
    void protectedEndpointReturns401WithNoToken() throws Exception {
        mockMvc.perform(get("/applications"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointReturns401WithInvalidToken() throws Exception {
        mockMvc.perform(get("/applications")
                .header("Authorization", "Bearer this.is.not.a.valid.token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointReturns401WithMalformedHeader() throws Exception {
        mockMvc.perform(get("/applications")
                .header("Authorization", "NotBearer sometoken"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanAccessProtectedEndpoint() throws Exception {
        // Register
        AuthDtos.RegisterRequest reg = new AuthDtos.RegisterRequest();
        reg.setEmail("dave@example.com");
        reg.setPassword("password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
            .andExpect(status().isCreated());

        // Login to get token
        AuthDtos.LoginRequest login = new AuthDtos.LoginRequest();
        login.setEmail("dave@example.com");
        login.setPassword("password123");
        String loginJson = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
            .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginJson).get("token").asText();

        // Access protected endpoint
        mockMvc.perform(get("/applications")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }
}
