package com.vietnam.pji.repository;

import com.vietnam.pji.model.agentic.RecommendationFinalSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationFinalSelectionRepository extends JpaRepository<RecommendationFinalSelection, Long> {
    Optional<RecommendationFinalSelection> findByEpisodeId(Long episodeId);
}
