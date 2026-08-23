package com.vietnam.pji.repository;

import com.vietnam.pji.model.agentic.DoctorFinalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface DoctorFinalDecisionRepository extends JpaRepository<DoctorFinalDecision, Long> {
    Optional<DoctorFinalDecision> findByReviewId(Long reviewId);

    Optional<DoctorFinalDecision> findByRunId(Long runId);

    List<DoctorFinalDecision> findByRunIdIn(Collection<Long> runIds);
}
