package com.jobtracker.application.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);
    boolean existsByIdAndUserId(Long id, Long userId);
    long countByUserIdAndStatus(Long userId, ApplicationStatus status);
    long countByUserId(Long userId);
    List<JobApplication> findTop5ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** One row per status the user has at least one application in. */
    @Query("select a.status as status, count(a) as count from JobApplication a "
         + "where a.user.id = :userId group by a.status")
    List<StatusCount> countByStatus(@Param("userId") Long userId);

    interface StatusCount {
        ApplicationStatus getStatus();
        long getCount();
    }
}
