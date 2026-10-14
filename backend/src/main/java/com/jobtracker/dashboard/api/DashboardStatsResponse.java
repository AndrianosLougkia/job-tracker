package com.jobtracker.dashboard.api;

import com.jobtracker.application.api.ApplicationSummary;
import com.jobtracker.application.domain.ApplicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dashboard statistics for one user. Cached as JSON, so it must stay a plain,
 * Jackson-serialisable value.
 *
 * @param byStatus           every status in enum order, zero-filled
 * @param interviewCount     applications currently in INTERVIEW
 * @param offerCount         applications currently in OFFER
 * @param recentApplications most recently created, newest first (at most 5)
 * @param generatedAt        when these numbers were computed; older than "now" means served from cache
 */
public record DashboardStatsResponse(
    long totalApplications,
    Map<ApplicationStatus, Long> byStatus,
    long interviewCount,
    long offerCount,
    List<ApplicationSummary> recentApplications,
    Instant generatedAt
) {}
