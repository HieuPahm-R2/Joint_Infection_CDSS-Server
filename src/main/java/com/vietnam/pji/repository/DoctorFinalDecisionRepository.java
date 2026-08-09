package com.vietnam.pji.repository;

import com.vietnam.pji.model.agentic.DoctorFinalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DoctorFinalDecisionRepository extends JpaRepository<DoctorFinalDecision, Long> {
    Optional<DoctorFinalDecision> findByReviewId(Long reviewId);
}
