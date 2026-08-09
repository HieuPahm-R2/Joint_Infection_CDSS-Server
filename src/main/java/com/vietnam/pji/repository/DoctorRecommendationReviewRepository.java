package com.vietnam.pji.repository;

import com.vietnam.pji.model.agentic.DoctorRecommendationReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorRecommendationReviewRepository
        extends JpaRepository<DoctorRecommendationReview, Long>,
        JpaSpecificationExecutor<DoctorRecommendationReview> {

    Optional<DoctorRecommendationReview> findByRunId(Long runId);

    List<DoctorRecommendationReview> findByEpisodeIdOrderByCreatedAtDesc(Long episodeId);

    Optional<DoctorRecommendationReview> findByEpisodeIdAndFinalDecisionTrue(Long episodeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DoctorRecommendationReview r
               SET r.finalDecision = false
             WHERE r.episode.id = :episodeId
               AND r.finalDecision = true
            """)
    void clearFinalDecisionForEpisode(@Param("episodeId") Long episodeId);
}
