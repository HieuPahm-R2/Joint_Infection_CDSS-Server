package com.vietnam.pji.services.episode;

import com.vietnam.pji.dto.response.EpisodeLockResponseDTO;

public interface EpisodeLockService {

    /**
     * Acquire a pessimistic edit lock on the given episode for {@code userId}.
     * Idempotent for the same holder (re-acquire refreshes the TTL).
     * Throws {@code ResourceBusyException} if another user already holds the lock.
     */
    EpisodeLockResponseDTO acquire(Long episodeId, Long userId);

    /**
     * Extend the lock TTL by another full window. Only the current holder can
     * renew.
     */
    EpisodeLockResponseDTO renew(Long episodeId, Long userId);

    /**
     * Release the lock. Only the current holder can release (no-op if already
     * expired).
     */
    void release(Long episodeId, Long userId);

    /** Require an existing lock owned by the caller before persisting an episode aggregate. */
    void assertHeldBy(Long episodeId, Long userId);
}
