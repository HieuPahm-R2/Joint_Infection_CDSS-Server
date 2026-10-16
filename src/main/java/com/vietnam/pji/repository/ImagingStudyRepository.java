package com.vietnam.pji.repository;

import com.vietnam.pji.model.medical.ImagingStudy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ImagingStudyRepository extends JpaRepository<ImagingStudy, Long>, JpaSpecificationExecutor<ImagingStudy> {
    Page<ImagingStudy> findByEpisodeId(Long episodeId, Pageable pageable);

    List<ImagingStudy> findByEpisodeIdOrderByCreatedAtDesc(Long episodeId);
}
