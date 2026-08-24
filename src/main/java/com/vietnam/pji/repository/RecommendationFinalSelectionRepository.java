package com.vietnam.pji.repository;

import com.vietnam.pji.model.agentic.RecommendationFinalSelection;
import com.vietnam.pji.constant.RecommendationScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface RecommendationFinalSelectionRepository extends JpaRepository<RecommendationFinalSelection, Long> {
    Optional<RecommendationFinalSelection> findByEpisodeIdAndRecommendationScope(
            Long episodeId,
            RecommendationScope recommendationScope);
    List<RecommendationFinalSelection> findAllByEpisodeId(Long episodeId);
}
