package com.vietnam.pji.services.episode;

import com.vietnam.pji.dto.request.EpisodeFullRequestDTO;
import com.vietnam.pji.dto.response.EpisodeFullResponseDTO;

/**
 * Reads and writes a whole episode aggregate (episode + medical history + clinical
 * record + surgeries + labs + images + cultures/sensitivities) in a single round-trip.
 *
 * The write is atomic: all child upserts/deletes run in one transaction, so a partial
 * save is impossible — replacing the previous client-orchestrated, non-atomic flow in
 * MedicalExamDetail.
 */
public interface EpisodeAggregateService {

    /** One transactional read of the full episode aggregate. */
    EpisodeFullResponseDTO getFull(Long episodeId);

    /**
     * Atomically upsert an episode and all its children.
     *
     * @param episodeId existing episode id to update, or {@code null} to create a new one
     */
    EpisodeFullResponseDTO saveFull(Long episodeId, EpisodeFullRequestDTO dto);

    /**
     * Atomically update an existing episode aggregate without rebuilding the full
     * read response.
     */
    void updateFull(Long episodeId, EpisodeFullRequestDTO dto);
}
