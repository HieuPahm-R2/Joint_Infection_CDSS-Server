package com.vietnam.pji.repository;

import com.vietnam.pji.model.agentic.PharmacistFinalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PharmacistFinalDecisionRepository extends JpaRepository<PharmacistFinalDecision, Long> {
    Optional<PharmacistFinalDecision> findByReviewId(Long reviewId);
}
