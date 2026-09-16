package com.vietnam.pji.repository;

import com.vietnam.pji.model.medical.PjiEpisode;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EpisodeRepository extends JpaRepository<PjiEpisode, Long>, JpaSpecificationExecutor<PjiEpisode> {
    Page<PjiEpisode> findByPatientId(Long patientId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT episode FROM PjiEpisode episode WHERE episode.id = :id")
    Optional<PjiEpisode> findByIdForUpdate(@Param("id") Long id);
}
