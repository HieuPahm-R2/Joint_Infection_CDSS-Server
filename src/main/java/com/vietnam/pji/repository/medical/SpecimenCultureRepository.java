package com.vietnam.pji.repository.medical;

import com.vietnam.pji.model.medical.SpecimenCulture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SpecimenCultureRepository
        extends JpaRepository<SpecimenCulture, Long>, JpaSpecificationExecutor<SpecimenCulture> {
    Page<SpecimenCulture> findByEpisodeId(Long episodeId, Pageable pageable);

    List<SpecimenCulture> findByEpisodeIdOrderByCreatedAtDesc(Long episodeId);
}
