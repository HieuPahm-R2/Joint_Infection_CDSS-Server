package com.vietnam.pji.repository.medical;

import com.vietnam.pji.model.medical.ClinicalAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ClinicalAssessmentRepository
        extends JpaRepository<ClinicalAssessment, Long>, JpaSpecificationExecutor<ClinicalAssessment> {
    Page<ClinicalAssessment> findByEpisodeId(Long episodeId, Pageable pageable);

    Optional<ClinicalAssessment> findFirstByEpisodeIdOrderByCreatedAtDesc(Long episodeId);
}
