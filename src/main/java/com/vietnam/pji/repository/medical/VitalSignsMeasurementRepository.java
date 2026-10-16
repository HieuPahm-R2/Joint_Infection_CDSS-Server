package com.vietnam.pji.repository.medical;

import com.vietnam.pji.model.medical.VitalSignsMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VitalSignsMeasurementRepository extends JpaRepository<VitalSignsMeasurement, Long> {

    List<VitalSignsMeasurement> findByEpisodeIdOrderByRecordedAtDesc(Long episodeId);

    /**
     * The most recent measurement, used to decide whether a save actually changed
     * the numbers and therefore deserves a new row in the history.
     */
    Optional<VitalSignsMeasurement> findFirstByEpisodeIdOrderByRecordedAtDesc(Long episodeId);
}
