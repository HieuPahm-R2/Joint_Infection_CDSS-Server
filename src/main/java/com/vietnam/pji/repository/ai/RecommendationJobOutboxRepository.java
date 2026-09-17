package com.vietnam.pji.repository.ai;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.vietnam.pji.model.agentic.RecommendationJobOutbox;

@Repository
public interface RecommendationJobOutboxRepository extends JpaRepository<RecommendationJobOutbox, Long> {

    @Query(value = """
            SELECT *
            FROM recommendation_job_outbox
            WHERE available_at <= CURRENT_TIMESTAMP
            ORDER BY available_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<RecommendationJobOutbox> findNextReadyForUpdate();
}
