package com.jobtracker.analysis.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    List<Analysis> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    List<Analysis> findByUserIdAndApplicationIdOrderByCreatedAtDescIdDesc(Long userId, Long applicationId);
    Optional<Analysis> findByIdAndUserId(Long id, Long userId);
    List<Analysis> findByStatusIn(Collection<AnalysisStatus> statuses);
    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);
    Optional<Analysis> findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, Instant since);
}
