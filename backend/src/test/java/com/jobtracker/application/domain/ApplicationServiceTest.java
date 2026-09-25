package com.jobtracker.application.domain;

import com.jobtracker.application.api.ApplicationResponse;
import com.jobtracker.application.api.CreateApplicationRequest;
import com.jobtracker.application.api.UpdateApplicationRequest;
import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock UserRepository userRepository;

    @InjectMocks ApplicationService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("dev@example.com", "hash");
        // Inject an ID via reflection — entity constructor doesn't set it
        setId(user, 1L);
    }

    @Test
    void listApplicationsReturnsOnlyUsersApplications() {
        JobApplication app = new JobApplication(user, "Acme", "Dev");
        setId(app, 10L);
        when(applicationRepository.findByUserIdOrderByCreatedAtDesc(1L))
            .thenReturn(List.of(app));

        List<ApplicationResponse> result = service.listApplications(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompany()).isEqualTo("Acme");
    }

    @Test
    void getApplicationThrowsWhenNotFound() {
        when(applicationRepository.findByIdAndUserId(99L, 1L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApplication(99L, 1L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createApplicationPersistsAndReturnsDto() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        JobApplication saved = new JobApplication(user, "Acme", "Engineer");
        setId(saved, 5L);
        when(applicationRepository.save(any(JobApplication.class))).thenReturn(saved);

        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("Acme");
        req.setRole("Engineer");

        ApplicationResponse response = service.createApplication(req, 1L);

        assertThat(response.getCompany()).isEqualTo("Acme");
        assertThat(response.getRole()).isEqualTo("Engineer");
        verify(applicationRepository).save(any());
    }

    @Test
    void createApplicationThrowsWhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("X");
        req.setRole("Y");

        assertThatThrownBy(() -> service.createApplication(req, 999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateApplicationChangesOnlyProvidedFields() {
        JobApplication existing = new JobApplication(user, "OldCo", "Old Role");
        setId(existing, 7L);
        when(applicationRepository.findByIdAndUserId(7L, 1L))
            .thenReturn(Optional.of(existing));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateApplicationRequest req = new UpdateApplicationRequest();
        req.setStatus(ApplicationStatus.APPLIED);

        ApplicationResponse result = service.updateApplication(7L, req, 1L);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(result.getCompany()).isEqualTo("OldCo"); // unchanged
    }

    @Test
    void deleteApplicationThrowsForWrongUser() {
        when(applicationRepository.findByIdAndUserId(1L, 99L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteApplication(1L, 99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // Reflection helper — sets the ID on entities that don't expose a setter
    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
