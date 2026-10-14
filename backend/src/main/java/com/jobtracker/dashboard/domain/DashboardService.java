package com.jobtracker.dashboard.domain;

import com.jobtracker.application.domain.ApplicationService;
import com.jobtracker.application.domain.ApplicationStatistics;
import com.jobtracker.application.domain.ApplicationStatus;
import com.jobtracker.dashboard.api.DashboardStatsResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Per-user dashboard statistics, cached under {@value #STATS_CACHE} keyed by user ID.
 *
 * The user ID is the whole cache key, so one user's entry can never be served to
 * another. Entries expire after the configured TTL and are evicted after any change
 * to the user's applications (see DashboardCacheInvalidator). If the cache is down,
 * every call computes fresh numbers from PostgreSQL.
 */
@Service
public class DashboardService {

    public static final String STATS_CACHE = "dashboard:stats";

    private final ApplicationService applicationService;
    private final Clock clock;

    public DashboardService(ApplicationService applicationService, Clock clock) {
        this.applicationService = applicationService;
        this.clock = clock;
    }

    @Cacheable(cacheNames = STATS_CACHE, key = "#userId")
    public DashboardStatsResponse getStats(Long userId) {
        ApplicationStatistics stats = applicationService.getStatistics(userId);
        return new DashboardStatsResponse(
            stats.total(),
            stats.byStatus(),
            stats.byStatus().getOrDefault(ApplicationStatus.INTERVIEW, 0L),
            stats.byStatus().getOrDefault(ApplicationStatus.OFFER, 0L),
            stats.recent(),
            Instant.now(clock));
    }

    @CacheEvict(cacheNames = STATS_CACHE, key = "#userId")
    public void evictStats(Long userId) {
        // Eviction is done by the annotation.
    }
}
