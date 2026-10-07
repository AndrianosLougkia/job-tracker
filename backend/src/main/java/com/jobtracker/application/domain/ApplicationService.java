package com.jobtracker.application.domain;

import com.jobtracker.application.api.ApplicationResponse;
import com.jobtracker.application.api.CreateApplicationRequest;
import com.jobtracker.application.api.UpdateApplicationRequest;
import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    public ApplicationService(ApplicationRepository applicationRepository,
                               UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
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

    public ApplicationResponse createApplication(CreateApplicationRequest request, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.user(userId));
        JobApplication app = new JobApplication(user, request.getCompany(), request.getRole());
        app.setJobDescription(request.getJobDescription());
        app.setNotes(request.getNotes());
        app.setAppliedAt(request.getAppliedAt());
        return ApplicationResponse.from(applicationRepository.save(app));
    }

    public ApplicationResponse updateApplication(Long id, UpdateApplicationRequest request, Long userId) {
        JobApplication app = findOwnedOrThrow(id, userId);
        if (request.getCompany() != null)        app.setCompany(request.getCompany());
        if (request.getRole() != null)           app.setRole(request.getRole());
        if (request.getStatus() != null)         app.setStatus(request.getStatus());
        if (request.getJobDescription() != null) app.setJobDescription(request.getJobDescription());
        if (request.getNotes() != null)          app.setNotes(request.getNotes());
        if (request.getAppliedAt() != null)      app.setAppliedAt(request.getAppliedAt());
        return ApplicationResponse.from(applicationRepository.save(app));
    }

    public void deleteApplication(Long id, Long userId) {
        applicationRepository.delete(findOwnedOrThrow(id, userId));
    }

    private JobApplication findOwnedOrThrow(Long id, Long userId) {
        return applicationRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> ResourceNotFoundException.application(id));
    }
}
