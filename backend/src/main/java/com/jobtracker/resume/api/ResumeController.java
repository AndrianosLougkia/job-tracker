package com.jobtracker.resume.api;

import com.jobtracker.resume.domain.ResumeService;
import com.jobtracker.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/resumes")
@Tag(name = "Resumes", description = "Resume PDF upload and management")
@SecurityRequirement(name = "bearerAuth")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping
    @Operation(summary = "List all resumes for the authenticated user")
    public List<ResumeResponse> list(@AuthenticationPrincipal User user) {
        return resumeService.listResumes(user.getId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get resume metadata by ID")
    public ResumeResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return resumeService.getResume(id, user.getId());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a resume PDF")
    public ResponseEntity<ResumeResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {
        ResumeResponse created = resumeService.upload(file, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        resumeService.deleteResume(id, user.getId());
    }
}
