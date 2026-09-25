package com.jobtracker.application.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<JobApplication, Long> {

    /** All applications belonging to this user, newest first. */
    List<JobApplication> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Single application — only returned if it belongs to the given user. */
    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    /** Ownership check without loading the full entity. */
    boolean existsByIdAndUserId(Long id, Long userId);

    /** Count by status for dashboard statistics (Stage 9). */
    long countByUserIdAndStatus(Long userId, ApplicationStatus status);

    /** Total application count for a user. */
    long countByUserId(Long userId);
}
