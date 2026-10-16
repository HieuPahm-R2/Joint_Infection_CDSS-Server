package com.vietnam.pji.services.episode;

import com.vietnam.pji.dto.request.AdmissionFullRequestDTO;
import com.vietnam.pji.dto.response.AdmissionFullResponseDTO;

/**
 * Reads and writes a whole episode aggregate (episode + medical history + clinical
 * record + surgeries + labs + images + cultures/sensitivities) in a single round-trip.
 *
 * The write is atomic: all child upserts/deletes run in one transaction, so a partial
 * save is impossible — replacing the previous client-orchestrated, non-atomic flow in
 * MedicalExamDetail.
 */
public interface AdmissionAggregateService {

    /** One transactional read of the full episode aggregate. */
    AdmissionFullResponseDTO getFull(Long episodeId);

    /**
     * Atomically upsert an episode and all its children.
     *
     * @param episodeId existing episode id to update, or {@code null} to create a new one
     */
    AdmissionFullResponseDTO saveFull(Long episodeId, AdmissionFullRequestDTO dto);

    /**
     * Atomically update an existing episode aggregate without rebuilding the full
     * read response.
     */
    void updateFull(Long episodeId, AdmissionFullRequestDTO dto);
}
