package com.vietnam.pji.repository.ai;

import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiRecommendationRunRepository extends JpaRepository<AiRecommendationRun, Long>,
        JpaSpecificationExecutor<AiRecommendationRun> {

    Page<AiRecommendationRun> findByEpisodeIdOrderByCreatedAtDesc(Long episodeId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(r.runNo), 0) FROM AiRecommendationRun r WHERE r.episode.id = :episodeId")
    int findMaxRunNoByEpisodeId(Long episodeId);

    Optional<AiRecommendationRun> findByRequestId(String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AiRecommendationRun r WHERE r.id = :id")
    Optional<AiRecommendationRun> findByIdForUpdate(@Param("id") Long id);

    boolean existsByRequestIdAndStatus(String requestId, RunStatus status);
}
