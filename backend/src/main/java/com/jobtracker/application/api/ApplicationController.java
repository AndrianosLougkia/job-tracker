package com.jobtracker.application.api;

import com.jobtracker.application.domain.ApplicationService;
import com.jobtracker.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/applications")
@Tag(name = "Applications", description = "Job application CRUD")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final ApplicationService applicationService;
    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    @Operation(summary = "List all applications for the authenticated user")
    public List<ApplicationResponse> list(@AuthenticationPrincipal User user) {
        return applicationService.listApplications(user.getId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single application by ID")
    public ApplicationResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return applicationService.getApplication(id, user.getId());
    }

    @PostMapping
    @Operation(summary = "Create a new job application")
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(applicationService.createApplication(request, user.getId()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an existing application (partial update)")
    public ApplicationResponse update(@PathVariable Long id,
            @Valid @RequestBody UpdateApplicationRequest request,
            @AuthenticationPrincipal User user) {
        return applicationService.updateApplication(id, request, user.getId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an application")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        applicationService.deleteApplication(id, user.getId());
    }
}
