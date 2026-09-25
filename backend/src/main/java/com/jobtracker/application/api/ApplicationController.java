package com.jobtracker.application.api;

import com.jobtracker.application.domain.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for job application CRUD.
 *
 * STAGE 2 NOTE: User identity is taken from the X-Dev-User-Id header for local
 * development and testing. This header is removed entirely in Stage 3, where the
 * authenticated user ID is extracted from the validated JWT principal instead.
 * Never use this pattern in production code.
 */
@RestController
@RequestMapping("/applications")
@Tag(name = "Applications", description = "Job application CRUD")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    @Operation(summary = "List all applications for the current user")
    public List<ApplicationResponse> list(
            @RequestHeader(value = "X-Dev-User-Id", defaultValue = "1") Long userId) {
        return applicationService.listApplications(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single application by ID")
    public ApplicationResponse get(
            @PathVariable Long id,
            @RequestHeader(value = "X-Dev-User-Id", defaultValue = "1") Long userId) {
        return applicationService.getApplication(id, userId);
    }

    @PostMapping
    @Operation(summary = "Create a new job application")
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest request,
            @RequestHeader(value = "X-Dev-User-Id", defaultValue = "1") Long userId) {
        ApplicationResponse created = applicationService.createApplication(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an existing application (partial update)")
    public ApplicationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationRequest request,
            @RequestHeader(value = "X-Dev-User-Id", defaultValue = "1") Long userId) {
        return applicationService.updateApplication(id, request, userId);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an application")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Dev-User-Id", defaultValue = "1") Long userId) {
        applicationService.deleteApplication(id, userId);
    }
}
