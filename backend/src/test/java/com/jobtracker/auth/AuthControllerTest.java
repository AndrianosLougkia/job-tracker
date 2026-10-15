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

import java.util.UUID;

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
    //  Email case, password limits, brute-force protection                //
    // ------------------------------------------------------------------ //

    @Test
    void emailsAreCaseInsensitiveAndStoredLowerCase() throws Exception {
        register("Erin@Example.COM", "password123").andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("erin@example.com"));

        register("erin@example.com", "password123").andExpect(status().isConflict());
        login("ERIN@example.com", "password123").andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("erin@example.com"));
    }

    @Test
    void passwordOverBcryptLimitIsRejectedWith400() throws Exception {
        register("long@example.com", "a".repeat(73))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors", hasItem(containsString("password"))));
        // 30 characters, but 90 bytes in UTF-8
        register("multibyte@example.com", "€".repeat(30))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("72 bytes")));
    }

    @Test
    void overlongPasswordAtLoginIsJustInvalidCredentials() throws Exception {
        register("frank@example.com", "password123").andExpect(status().isCreated());

        login("frank@example.com", "€".repeat(30)).andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailedLoginsLockTheAccountTemporarily() throws Exception {
        String email = "grace-" + UUID.randomUUID() + "@example.com";
        register(email, "password123").andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password").andExpect(status().isUnauthorized());
        }
        // Even the right password is refused while locked
        login(email, "password123")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.message", containsString("Too many failed login attempts")));
        // Other accounts are unaffected
        String other = "heidi-" + UUID.randomUUID() + "@example.com";
        register(other, "password123").andExpect(status().isCreated());
        login(other, "password123").andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsTheFailureCount() throws Exception {
        String email = "ivan-" + UUID.randomUUID() + "@example.com";
        register(email, "password123").andExpect(status().isCreated());

        for (int i = 0; i < 4; i++) login(email, "wrong-password").andExpect(status().isUnauthorized());
        login(email, "password123").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) login(email, "wrong-password").andExpect(status().isUnauthorized());
        login(email, "password123").andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String password) throws Exception {
        AuthDtos.RegisterRequest req = new AuthDtos.RegisterRequest();
        req.setEmail(email);
        req.setPassword(password);
        return mockMvc.perform(post("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        AuthDtos.LoginRequest req = new AuthDtos.LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)));
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
