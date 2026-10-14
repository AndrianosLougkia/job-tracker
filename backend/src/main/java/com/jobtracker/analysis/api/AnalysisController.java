package com.jobtracker.analysis.api;

import com.jobtracker.analysis.domain.AnalysisService;
import com.jobtracker.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/analyses")
@Tag(name = "Analyses", description = "AI resume-versus-job-description analysis")
@SecurityRequirement(name = "bearerAuth")
public class AnalysisController {

    private final AnalysisService analysisService;
    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping
    @Operation(summary = "List analyses for the authenticated user, optionally filtered by application")
    public List<AnalysisResponse> list(@RequestParam(required = false) Long applicationId,
                                       @AuthenticationPrincipal User user) {
        return analysisService.listAnalyses(user.getId(), applicationId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single analysis by ID")
    public AnalysisResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return analysisService.getAnalysis(id, user.getId());
    }

    @PostMapping
    @Operation(summary = "Start analysing a resume against an application's job description",
        description = "Returns 202 with the analysis in PENDING status. Processing continues in the background; "
            + "the result is pushed to /user/queue/analyses over WebSocket and can be read from GET /analyses/{id}.")
    public ResponseEntity<AnalysisResponse> create(
            @Valid @RequestBody TriggerAnalysisRequest request,
            @AuthenticationPrincipal User user) {
        AnalysisResponse analysis = analysisService.requestAnalysis(
            request.getApplicationId(), request.getResumeId(), user.getId());
        return ResponseEntity.accepted()
            .location(URI.create("/api/analyses/" + analysis.getId()))
            .body(analysis);
    }
}
