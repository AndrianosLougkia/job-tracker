package com.jobtracker.application.domain;

import com.jobtracker.application.api.ApplicationResponse;
import com.jobtracker.application.api.ApplicationSummary;
import com.jobtracker.application.api.CreateApplicationRequest;
import com.jobtracker.application.api.UpdateApplicationRequest;
import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ApplicationService {

    public static final int RECENT_LIMIT = 5;

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ApplicationService(ApplicationRepository applicationRepository,
                               UserRepository userRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listApplications(Long userId) {
        return applicationRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream().map(ApplicationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(Long id, Long userId) {
        return ApplicationResponse.from(findOwnedOrThrow(id, userId));
    }

    /**
     * Job description of an owned application, for other modules (e.g. analysis).
     * Returns null if the application has no description.
     */
    @Transactional(readOnly = true)
    public String getJobDescription(Long id, Long userId) {
        return findOwnedOrThrow(id, userId).getJobDescription();
    }

    /** Counts per status (zero-filled, enum order) and the most recent applications. */
    @Transactional(readOnly = true)
    public ApplicationStatistics getStatistics(Long userId) {
        Map<ApplicationStatus, Long> byStatus = new LinkedHashMap<>();
        Arrays.stream(ApplicationStatus.values()).forEach(status -> byStatus.put(status, 0L));
        applicationRepository.countByStatus(userId)
            .forEach(row -> byStatus.put(row.getStatus(), row.getCount()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        List<ApplicationSummary> recent = applicationRepository.findTop5ByUserIdOrderByCreatedAtDescIdDesc(userId)
            .stream().map(ApplicationSummary::from).toList();
        return new ApplicationStatistics(total, byStatus, recent);
    }

    public ApplicationResponse createApplication(CreateApplicationRequest request, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.user(userId));
        JobApplication app = new JobApplication(user, request.getCompany(), request.getRole());
        if (request.getStatus() != null) app.setStatus(request.getStatus());
        app.setJobDescription(request.getJobDescription());
        app.setNotes(request.getNotes());
        app.setAppliedAt(request.getAppliedAt());
        ApplicationResponse created = ApplicationResponse.from(applicationRepository.save(app));
        eventPublisher.publishEvent(new ApplicationsChangedEvent(userId));
        return created;
    }

    public ApplicationResponse updateApplication(Long id, UpdateApplicationRequest request, Long userId) {
        JobApplication app = findOwnedOrThrow(id, userId);
        if (request.getCompany() != null)        app.setCompany(request.getCompany());
        if (request.getRole() != null)           app.setRole(request.getRole());
        if (request.getStatus() != null)         app.setStatus(request.getStatus());
        if (request.getJobDescription() != null) app.setJobDescription(request.getJobDescription());
        if (request.getNotes() != null)          app.setNotes(request.getNotes());
        if (request.getAppliedAt() != null)      app.setAppliedAt(request.getAppliedAt());
        ApplicationResponse updated = ApplicationResponse.from(applicationRepository.save(app));
        eventPublisher.publishEvent(new ApplicationsChangedEvent(userId));
        return updated;
    }

    public void deleteApplication(Long id, Long userId) {
        applicationRepository.delete(findOwnedOrThrow(id, userId));
        eventPublisher.publishEvent(new ApplicationsChangedEvent(userId));
    }

    private JobApplication findOwnedOrThrow(Long id, Long userId) {
        return applicationRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> ResourceNotFoundException.application(id));
    }
}
