package com.vietnam.pji.repository.ai;

import com.vietnam.pji.model.agentic.RuleBasedDiagnosticResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RuleBasedDiagnosticResultRepository extends JpaRepository<RuleBasedDiagnosticResult, Long> {

    Optional<RuleBasedDiagnosticResult> findByRunId(Long runId);
}
