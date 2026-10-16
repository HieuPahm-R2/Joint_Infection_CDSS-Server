package com.vietnam.pji.repository;

import com.vietnam.pji.model.medical.LabReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface LabReportRepository extends JpaRepository<LabReport, Long>, JpaSpecificationExecutor<LabReport> {
    Page<LabReport> findByEpisodeId(Long episodeId, Pageable pageable);

    Optional<LabReport> findFirstByEpisodeIdOrderByCreatedAtDesc(Long episodeId);

    List<LabReport> findTop5ByEpisodeIdOrderByCreatedAtDesc(Long episodeId);
}
