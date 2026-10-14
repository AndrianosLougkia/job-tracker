package com.jobtracker.dashboard.api;

import com.jobtracker.dashboard.domain.DashboardService;
import com.jobtracker.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Aggregate statistics for the authenticated user")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Application counts by status and recent applications",
        description = "Cached per user for up to the configured TTL (default 5 minutes) and refreshed "
            + "after any change to the user's applications. generatedAt shows when the numbers were computed.")
    public DashboardStatsResponse stats(@AuthenticationPrincipal User user) {
        return dashboardService.getStats(user.getId());
    }
}
