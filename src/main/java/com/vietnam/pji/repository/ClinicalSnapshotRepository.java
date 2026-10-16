package com.vietnam.pji.repository;

import com.vietnam.pji.model.agentic.ClinicalSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicalSnapshotRepository extends JpaRepository<ClinicalSnapshot, Long>,
        JpaSpecificationExecutor<ClinicalSnapshot> {

    @Query("SELECT COALESCE(MAX(s.snapshotNo), 0) FROM ClinicalSnapshot s WHERE s.episode.id = :episodeId")
    int findMaxSnapshotNoByEpisodeId(Long episodeId);
}
