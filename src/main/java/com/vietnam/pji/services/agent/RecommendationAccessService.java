package com.vietnam.pji.services.agent;

import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.auth.UserService;
import com.vietnam.pji.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationAccessService {

    private final EpisodeRepository episodeRepository;
    private final AiRecommendationRunRepository runRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public void assertCanGenerateEpisode(Long episodeId, RecommendationScope scope) {
        if (scope == null || scope == RecommendationScope.LEGACY_COMBINED) {
            throw new InvalidDataException("New recommendation runs require SURGERY or ANTIBIOTIC scope");
        }
        PjiEpisode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found: " + episodeId));
        Hibernate.initialize(episode.getPatient());

        String currentEmail = SecurityUtils.getCurrentUserLogin().orElse("");
        User user = isBlank(currentEmail) ? null : userService.handleGetUserByUsername(currentEmail);
        String roleName = user != null && user.getRole() != null ? user.getRole().getName() : "";
        boolean admin = "ADMIN".equalsIgnoreCase(roleName) || "SUPER_ADMIN".equalsIgnoreCase(roleName);

        if (scope == RecommendationScope.SURGERY) {
            if (!admin && !"DOCTOR".equalsIgnoreCase(roleName)) {
                throw new ForbiddenException("Only doctors can generate a surgery recommendation");
            }
            validateOwnerOrAdmin(episode, null);
            return;
        }

        if (!admin && !"PHARMACIST".equalsIgnoreCase(roleName)) {
            throw new ForbiddenException("Only pharmacists can generate an antibiotic recommendation");
        }
        if (!admin) {
            return;
        }
        validateOwnerOrAdmin(episode, null);
    }

    @Transactional(readOnly = true)
    public void assertCanAccessEpisode(Long episodeId) {
        PjiEpisode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found: " + episodeId));
        Hibernate.initialize(episode.getPatient());
        validateOwnerOrAdmin(episode, null);
    }

    @Transactional(readOnly = true)
    public void assertCanAccessRun(Long runId) {
        AiRecommendationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));
        Hibernate.initialize(run.getEpisode());
        PjiEpisode episode = run.getEpisode();
        if (episode == null) {
            throw new ForbiddenException("AI recommendation run is not linked to a medical record");
        }
        Hibernate.initialize(episode.getPatient());
        validateOwnerOrAdmin(episode, run);
    }

    @Transactional(readOnly = true)
    public void assertCanReviewEpisode(Long episodeId) {
        PjiEpisode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found: " + episodeId));
        Hibernate.initialize(episode.getPatient());
        validateReviewer(episode, null);
    }

    @Transactional(readOnly = true)
    public void assertCanReviewRun(Long runId) {
        AiRecommendationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + runId));
        Hibernate.initialize(run.getEpisode());
        PjiEpisode episode = run.getEpisode();
        if (episode == null) {
            throw new ForbiddenException("AI recommendation run is not linked to a medical record");
        }
        Hibernate.initialize(episode.getPatient());
        validateReviewer(episode, run);
    }

    private void validateReviewer(PjiEpisode episode, AiRecommendationRun run) {
        String currentEmail = SecurityUtils.getCurrentUserLogin().orElse("");
        if (isBlank(currentEmail)) {
            throw new ForbiddenException("You don't have permission to review this treatment plan");
        }
        User user = userService.handleGetUserByUsername(currentEmail);
        String roleName = user != null && user.getRole() != null ? user.getRole().getName() : "";
        if ("PHARMACIST".equalsIgnoreCase(roleName)) return;
        validateOwnerOrAdmin(episode, run);
    }

    private void validateOwnerOrAdmin(PjiEpisode episode, AiRecommendationRun run) {
        String currentEmail = SecurityUtils.getCurrentUserLogin().orElse("");
        if (isBlank(currentEmail)) {
            throw new ForbiddenException("You don't have permission to access this treatment plan");
        }

        if (isAdmin(currentEmail)) return;

        String patientCreatedBy = episode.getPatient() != null ? episode.getPatient().getCreatedBy() : null;
        String episodeCreatedBy = episode.getCreatedBy();
        String runCreatedBy = run != null ? run.getCreatedBy() : null;
        boolean hasOwnerMetadata = !isBlank(patientCreatedBy)
                || !isBlank(episodeCreatedBy)
                || !isBlank(runCreatedBy);

        if (hasOwnerMetadata
                && !sameUser(currentEmail, patientCreatedBy)
                && !sameUser(currentEmail, episodeCreatedBy)
                && !sameUser(currentEmail, runCreatedBy)) {
            throw new ForbiddenException("Only the owner of this medical record can access this treatment plan");
        }
    }

    private boolean isAdmin(String email) {
        User user = userService.handleGetUserByUsername(email);
        String roleName = user != null && user.getRole() != null ? user.getRole().getName() : "";
        return "ADMIN".equalsIgnoreCase(roleName) || "SUPER_ADMIN".equalsIgnoreCase(roleName);
    }

    private boolean sameUser(String currentEmail, String ownerEmail) {
        return !isBlank(ownerEmail) && currentEmail.trim().equalsIgnoreCase(ownerEmail.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
