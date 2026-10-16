package com.vietnam.pji.services.episode;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.services.feat.RedisService;

import lombok.RequiredArgsConstructor;

/**
 * The single owner of the "episode graph" side effects that every
 * episode-scoped writer shares:
 *
 * <ul>
 * <li>forcing the lazy {@code episode -> patient} association inside the
 * reading transaction so the controller can serialize it after the session
 * closes; and</li>
 * <li>invalidating the cached episode snapshot once the episode's clinical
 * data changed.</li>
 * </ul>
 *
 * <p>
 * Both were previously open-coded in each service (24 {@code
 * Hibernate.initialize} calls and 23 eviction calls). Centralizing them keeps
 * the two rules in one place: a new episode-scoped writer cannot forget the
 * cache eviction, and the N+1-style {@code findById} round-trips that services
 * used only to recover an episode id are gone.
 *
 * <p>
 * Every method must be called inside the caller's transaction; the eviction
 * methods emit no transaction of their own.
 */
@Component
@RequiredArgsConstructor
public class AdmissionGraphLoader {

    private final RedisService redisService;

    /**
     * Loads {@code episode.patient} on an episode that is already in hand.
     *
     * @param episode the episode whose patient must be serializable; {@code null}
     *                is ignored
     */
    public void loadPatient(Admission episode) {
        if (episode != null) {
            Hibernate.initialize(episode.getPatient());
        }
    }

    /**
     * Evicts the cached snapshot of the episode that owns {@code episodeId}.
     *
     * @param episodeId target episode; {@code null} is ignored so callers can
     *                  pass the result of a best-effort lookup
     */
    public void evictSnapshot(Long episodeId) {
        if (episodeId != null) {
            redisService.evictSnapshotCache(episodeId);
        }
    }
}
