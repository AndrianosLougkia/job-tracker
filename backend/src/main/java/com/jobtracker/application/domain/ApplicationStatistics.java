package com.jobtracker.application.domain;

import com.jobtracker.application.api.ApplicationSummary;

import java.util.List;
import java.util.Map;

/**
 * Aggregate view of one user's applications.
 *
 * @param byStatus every status, in enum order, including those with a count of zero
 * @param recent   up to {@link ApplicationService#RECENT_LIMIT} most recently created
 */
public record ApplicationStatistics(long total, Map<ApplicationStatus, Long> byStatus, List<ApplicationSummary> recent) {}
