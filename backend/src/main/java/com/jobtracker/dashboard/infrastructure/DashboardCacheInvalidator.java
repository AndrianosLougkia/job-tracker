package com.jobtracker.dashboard.infrastructure;

import com.jobtracker.application.domain.ApplicationsChangedEvent;
import com.jobtracker.dashboard.domain.DashboardService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts a user's cached dashboard statistics when their applications change.
 *
 * Runs after the change commits. Evicting earlier would let a dashboard request that
 * arrives in between read the old rows and put them straight back into the cache.
 */
@Component
public class DashboardCacheInvalidator {

    private final DashboardService dashboardService;

    public DashboardCacheInvalidator(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationsChanged(ApplicationsChangedEvent event) {
        dashboardService.evictStats(event.userId());
    }
}
