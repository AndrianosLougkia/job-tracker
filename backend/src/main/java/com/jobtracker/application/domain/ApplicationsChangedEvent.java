package com.jobtracker.application.domain;

/**
 * Published when a user's applications are created, updated or deleted, so other
 * modules (e.g. the dashboard cache) can react without the application module
 * knowing about them.
 */
public record ApplicationsChangedEvent(Long userId) {}
