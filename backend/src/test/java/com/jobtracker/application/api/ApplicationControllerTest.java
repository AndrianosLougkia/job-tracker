package com.jobtracker.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.AbstractIntegrationTest;
import com.jobtracker.application.domain.ApplicationRepository;
import com.jobtracker.user.domain.User;
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
class ApplicationControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        User user = userRepository.save(new User("test@example.com", "hash"));
        userId = user.getId();
    }

    @Test
    void createApplicationReturns201() throws Exception {
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("Acme Corp");
        req.setRole("Backend Engineer");

        mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Dev-User-Id", userId)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.company").value("Acme Corp"))
            .andExpect(jsonPath("$.role").value("Backend Engineer"))
            .andExpect(jsonPath("$.status").value("WISHLIST"))
            .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void createApplicationValidatesRequiredFields() throws Exception {
        CreateApplicationRequest req = new CreateApplicationRequest(); // blank company and role

        mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Dev-User-Id", userId)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void listApplicationsReturnsOnlyUsersOwnApplications() throws Exception {
        // Create an application for userId
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("My Corp");
        req.setRole("Dev");
        mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Dev-User-Id", userId)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());

        // Create another user and application
        User other = userRepository.save(new User("other@example.com", "hash"));
        req.setCompany("Other Corp");
        mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Dev-User-Id", other.getId())
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());

        // List for userId — should only see their own
        mockMvc.perform(get("/applications")
                .header("X-Dev-User-Id", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].company").value("My Corp"));
    }

    @Test
    void getApplicationReturns404ForWrongUser() throws Exception {
        // Create application under userId
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("Secret Corp");
        req.setRole("Dev");

        String json = mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Dev-User-Id", userId)
                .content(objectMapper.writeValueAsString(req)))
            .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(json).get("id").asLong();

        // Another user tries to access it
        User other = userRepository.save(new User("intruder@example.com", "hash"));
        mockMvc.perform(get("/applications/" + appId)
                .header("X-Dev-User-Id", other.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateApplicationChangesStatus() throws Exception {
        CreateApplicationRequest create = new CreateApplicationRequest();
        create.setCompany("Acme");
        create.setRole("Dev");

        String json = mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Dev-User-Id", userId)
                .content(objectMapper.writeValueAsString(create)))
            .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(json).get("id").asLong();

        UpdateApplicationRequest update = new UpdateApplicationRequest();
        update.setStatus(com.jobtracker.application.domain.ApplicationStatus.APPLIED);

        mockMvc.perform(patch("/applications/" + appId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Dev-User-Id", userId)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPLIED"))
            .andExpect(jsonPath("$.company").value("Acme")); // unchanged
    }

    @Test
    void deleteApplicationReturns204() throws Exception {
        CreateApplicationRequest create = new CreateApplicationRequest();
        create.setCompany("Del Corp");
        create.setRole("Dev");

        String json = mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Dev-User-Id", userId)
                .content(objectMapper.writeValueAsString(create)))
            .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(json).get("id").asLong();

        mockMvc.perform(delete("/applications/" + appId)
                .header("X-Dev-User-Id", userId))
            .andExpect(status().isNoContent());

        // Confirm gone
        mockMvc.perform(get("/applications/" + appId)
                .header("X-Dev-User-Id", userId))
            .andExpect(status().isNotFound());
    }
}
