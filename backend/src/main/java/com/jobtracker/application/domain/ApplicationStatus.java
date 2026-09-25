package com.jobtracker.application.domain;

/**
 * Lifecycle status of a job application.
 * Values must match the PostgreSQL application_status ENUM defined in V2 migration.
 */
public enum ApplicationStatus {
    WISHLIST,
    APPLIED,
    SCREENING,
    INTERVIEW,
    OFFER,
    REJECTED,
    WITHDRAWN
}
