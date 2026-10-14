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
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock ApplicationEventPublisher eventPublisher;

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
        verify(eventPublisher).publishEvent(new ApplicationsChangedEvent(1L));
    }

    @Test
    void createApplicationThrowsWhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setCompany("X");
        req.setRole("Y");

        assertThatThrownBy(() -> service.createApplication(req, 999L))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(eventPublisher);
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
        verify(eventPublisher).publishEvent(new ApplicationsChangedEvent(1L));
    }

    @Test
    void deleteApplicationThrowsForWrongUser() {
        when(applicationRepository.findByIdAndUserId(1L, 99L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteApplication(1L, 99L))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteApplicationPublishesChangeForOwner() {
        JobApplication existing = new JobApplication(user, "Acme", "Dev");
        setId(existing, 3L);
        when(applicationRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(existing));

        service.deleteApplication(3L, 1L);

        verify(applicationRepository).delete(existing);
        verify(eventPublisher).publishEvent(new ApplicationsChangedEvent(1L));
    }

    @Test
    void statisticsAreZeroFilledInStatusOrderAndTotalled() {
        when(applicationRepository.countByStatus(1L)).thenReturn(List.of(
            statusCount(ApplicationStatus.INTERVIEW, 2), statusCount(ApplicationStatus.APPLIED, 3)));
        JobApplication recent = new JobApplication(user, "Acme", "Dev");
        setId(recent, 4L);
        when(applicationRepository.findTop5ByUserIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of(recent));

        ApplicationStatistics stats = service.getStatistics(1L);

        assertThat(stats.total()).isEqualTo(5);
        assertThat(stats.byStatus().keySet()).containsExactly(ApplicationStatus.values());
        assertThat(stats.byStatus()).containsEntry(ApplicationStatus.APPLIED, 3L)
            .containsEntry(ApplicationStatus.INTERVIEW, 2L)
            .containsEntry(ApplicationStatus.OFFER, 0L);
        assertThat(stats.recent()).singleElement()
            .satisfies(summary -> assertThat(summary.company()).isEqualTo("Acme"));
    }

    private static ApplicationRepository.StatusCount statusCount(ApplicationStatus status, long count) {
        return new ApplicationRepository.StatusCount() {
            @Override public ApplicationStatus getStatus() { return status; }
            @Override public long getCount() { return count; }
        };
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
