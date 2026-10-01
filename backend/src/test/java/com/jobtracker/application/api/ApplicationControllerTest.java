package com.jobtracker.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.AbstractIntegrationTest;
import com.jobtracker.TestJwtHelper;
import com.jobtracker.application.domain.ApplicationRepository;
import com.jobtracker.application.domain.ApplicationStatus;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ApplicationControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TestJwtHelper jwtHelper;

    private User user;
    private String authHeader;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(new User("test@example.com",
            passwordEncoder.encode("password")));
        authHeader = jwtHelper.authHeader(user);
    }

    @Test
    void createApplicationReturns201() throws Exception {
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("Acme Corp");
        req.setRole("Backend Engineer");

        mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.company").value("Acme Corp"))
            .andExpect(jsonPath("$.role").value("Backend Engineer"))
            .andExpect(jsonPath("$.status").value("WISHLIST"))
            .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void createApplicationReturns401WithNoToken() throws Exception {
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("Acme");
        req.setRole("Dev");

        mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createApplicationValidatesRequiredFields() throws Exception {
        CreateApplicationRequest req = new CreateApplicationRequest();

        mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void listApplicationsReturnsOnlyUsersOwnApplications() throws Exception {
        // Create application for primary user
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("My Corp");
        req.setRole("Dev");
        mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());

        // Create another user and their application
        User other = userRepository.save(new User("other@example.com",
            passwordEncoder.encode("password")));
        String otherHeader = jwtHelper.authHeader(other);
        req.setCompany("Other Corp");
        mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", otherHeader)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());

        // List for primary user — must see only their own
        mockMvc.perform(get("/applications")
                .header("Authorization", authHeader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].company").value("My Corp"));
    }

    @Test
    void getApplicationReturns404ForWrongUser() throws Exception {
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("Secret Corp");
        req.setRole("Dev");

        String json = mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(objectMapper.writeValueAsString(req)))
            .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(json).get("id").asLong();

        User intruder = userRepository.save(new User("intruder@example.com",
            passwordEncoder.encode("password")));

        mockMvc.perform(get("/applications/" + appId)
                .header("Authorization", jwtHelper.authHeader(intruder)))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateApplicationChangesStatus() throws Exception {
        CreateApplicationRequest create = new CreateApplicationRequest();
        create.setCompany("Acme");
        create.setRole("Dev");

        String json = mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(objectMapper.writeValueAsString(create)))
            .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(json).get("id").asLong();

        UpdateApplicationRequest update = new UpdateApplicationRequest();
        update.setStatus(ApplicationStatus.APPLIED);

        mockMvc.perform(patch("/applications/" + appId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPLIED"))
            .andExpect(jsonPath("$.company").value("Acme"));
    }

    @Test
    void deleteApplicationReturns204() throws Exception {
        CreateApplicationRequest create = new CreateApplicationRequest();
        create.setCompany("Del Corp");
        create.setRole("Dev");

        String json = mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .content(objectMapper.writeValueAsString(create)))
            .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(json).get("id").asLong();

        mockMvc.perform(delete("/applications/" + appId)
                .header("Authorization", authHeader))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/applications/" + appId)
                .header("Authorization", authHeader))
            .andExpect(status().isNotFound());
    }
}
