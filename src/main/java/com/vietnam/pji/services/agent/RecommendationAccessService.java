package com.vietnam.pji.services.agent;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.repository.AdmissionRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.auth.CurrentActorProvider;
import com.vietnam.pji.services.episode.AdmissionRecordAccess;
import com.vietnam.pji.utils.ActivePrincipal;

import lombok.RequiredArgsConstructor;

/**
 * Authorization for the AI recommendation workflows: it checks and returns
 * nothing.
 * <ol>
 * <li><strong>Functional lane</strong> - which discipline may act for a given
 * {@link RecommendationScope}: a doctor for a surgery run, a pharmacist for an
 * antibiotic run. Owned by this class.</li>
 * <li><strong>Record relationship</strong> - whether the caller may reach this
 * particular episode at all. Owned by {@link AdmissionRecordAccess}, so it is
 * the
 * same decision the clinical-record endpoints make.</li>
 * </ol>
 *
 * The previous {@code assertCanGenerateEpisode} branched per scope and both
 * branches were wrong in opposite directions:
 */
@Service
@RequiredArgsConstructor
public class RecommendationAccessService {

    private final AdmissionRepository episodeRepository;
    private final AiRecommendationRunRepository runRepository;
    private final CurrentActorProvider actorProvider;
    private final AdmissionRecordAccess recordAccess;

    /**
     * Requires an active principal in the scope's professional lane with access to
     * the episode.
     *
     * @throws InvalidDataException when the scope is missing
     */
    @Transactional(readOnly = true)
    public void assertCanGenerateEpisode(Long episodeId, RecommendationScope scope) {
        if (scope == null) {
            throw new InvalidDataException("New recommendation runs require SURGERY or ANTIBIOTIC scope");
        }
        ActivePrincipal principal = actorProvider.require();
        requireLane(principal, scope);

        Admission episode = requireEpisode(episodeId);
        recordAccess.assertCanAccess(principal, episode, null);
    }

    @Transactional(readOnly = true)
    public void assertCanAccessEpisode(Long episodeId) {
        ActivePrincipal principal = actorProvider.require();
        Admission episode = requireEpisode(episodeId);
        recordAccess.assertCanAccess(principal, episode, null);
    }

    @Transactional(readOnly = true)
    public void assertCanAccessRun(Long runId) {
        ActivePrincipal principal = actorProvider.require();
        AiRecommendationRun run = requireRun(runId);
        recordAccess.assertCanAccess(principal, requireRunEpisode(run), run);
    }

    /**
     * Requires the principal to be able to review the episode's clinical
     * decisions.
     *
     * <p>
     * A pharmacist is a reviewer by discipline for the antibiotic lane and still
     * has to reach the record; the previous implementation returned early for
     * every pharmacist, which is what let any pharmacist read any episode.
     */
    @Transactional(readOnly = true)
    public void assertCanReviewEpisode(Long episodeId) {
        ActivePrincipal principal = actorProvider.require();
        Admission episode = requireEpisode(episodeId);
        recordAccess.assertCanAccess(principal, episode, null);
    }

    @Transactional(readOnly = true)
    public void assertCanReviewRun(Long runId) {
        ActivePrincipal principal = actorProvider.require();
        AiRecommendationRun run = requireRun(runId);
        recordAccess.assertCanAccess(principal, requireRunEpisode(run), run);
    }

    /**
     * The scope's professional lane, with the administrator lane as break-glass.
     */
    private void requireLane(ActivePrincipal principal, RecommendationScope scope) {
        if (principal.admin()) {
            return;
        }
        boolean inLane = switch (scope) {
            case SURGERY -> principal.hasRole("DOCTOR");
            case ANTIBIOTIC -> principal.hasRole("PHARMACIST");
        };
        if (!inLane) {
            throw new ForbiddenException(switch (scope) {
                case SURGERY -> "Only doctors can generate a surgery recommendation";
                case ANTIBIOTIC -> "Only pharmacists can generate an antibiotic recommendation";
            });
        }
    }

    private Admission requireEpisode(Long episodeId) {
        Admission episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found: " + episodeId));
        Hibernate.initialize(episode.getPatient());
        return episode;
    }

    private AiRecommendationRun requireRun(Long runId) {
        AiRecommendationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));
        Hibernate.initialize(run.getEpisode());
        return run;
    }

    private Admission requireRunEpisode(AiRecommendationRun run) {
        Admission episode = run.getEpisode();
        if (episode == null) {
            throw new ForbiddenException("AI recommendation run is not linked to a medical record");
        }
        Hibernate.initialize(episode.getPatient());
        return episode;
    }
}
