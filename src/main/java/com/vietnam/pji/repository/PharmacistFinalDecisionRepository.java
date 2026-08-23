package com.vietnam.pji.repository;

import com.vietnam.pji.model.agentic.PharmacistFinalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PharmacistFinalDecisionRepository extends JpaRepository<PharmacistFinalDecision, Long> {
    Optional<PharmacistFinalDecision> findByRunId(Long runId);

    List<PharmacistFinalDecision> findByRunIdIn(Collection<Long> runIds);
}
