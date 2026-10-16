package com.vietnam.pji.services.episode;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.medical.EpisodeCareTeamRepository;
import com.vietnam.pji.services.auth.ActivePrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The one place that decides whether a principal may reach a clinical record.
 *
 * <p>
 * Implements the record-relationship layer of
 * {@code docs/decisions/0008-general-authorization-policy.md}: access requires an
 * {@link ActivePrincipal}, and then ownership, explicit care-team membership, or
 * an audited administrator break-glass. This replaces four divergent copies of
 * the same decision, two of which failed open.
 *
 * <h2>Why the old check failed open</h2>
 *
 * The previous implementation keyed ownership off free-text
 * {@code created_by} email columns and granted access whenever that text was
 * blank:
 *
 * <pre>
 * boolean hasOwnerMetadata = !isBlank(patientCreatedBy) || !isBlank(episodeCreatedBy) || !isBlank(runCreatedBy);
 * if (hasOwnerMetadata &amp;&amp; !sameUser(...) &amp;&amp; !sameUser(...) &amp;&amp; !sameUser(...)) {
 *     throw new ForbiddenException(...);   // no else: blank metadata == allowed
 * }
 * </pre>
 *
 * {@code AbstractEntity.handleBeforeCreate} writes the literal {@code " "} as
 * {@code createdBy} when there is no principal, so every record persisted outside
 * an authenticated request was readable by anyone - while
 * {@code docs/plans/active/authorization-hardening.md} says such records "remain
 * admin-only". Absence of evidence is now a denial.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EpisodeRecordAccess {

    private final EpisodeCareTeamRepository careTeamRepository;

    /**
     * Enforces access to {@code episode} for {@code principal}.
     *
     * @param principal the validated caller
     * @param episode   the episode being reached
     * @param run       the specific run being reached, when the operation is
     *                  run-scoped; its creator is accepted as a legacy owner
     *                  signal. May be {@code null}.
     * @throws ForbiddenException when the principal is neither an active
     *                            administrator, nor a care-team member, nor a
     *                            resolvable legacy creator
     */
    @Transactional(readOnly = true)
    public void assertCanAccess(ActivePrincipal principal, PjiEpisode episode, AiRecommendationRun run) {
        if (principal == null || episode == null) {
            throw new ForbiddenException("You don't have permission to access this treatment plan");
        }

        if (principal.admin()) {
            // Decision 0008 requires the bypass to emit a security-audit event.
            // ADMIN exists as a compatibility break-glass role, so every use is
            // recorded with enough context to reconstruct what was reached.
            log.warn("SECURITY_AUDIT admin_break_glass principalUserId={} episodeId={} runId={}",
                    principal.userId(), episode.getId(), run == null ? null : run.getId());
            return;
        }

        if (episode.getId() != null
                && careTeamRepository.existsActiveMember(episode.getId(), principal.userId())) {
            return;
        }

        if (isResolvableLegacyCreator(principal, episode, run)) {
            return;
        }

        // Blank, stale or unresolvable ownership metadata denies non-admins. This
        // is the fix for the fail-open branch described above.
        throw new ForbiddenException("Only a member of this episode's care team can access this treatment plan");
    }

    /**
     * Transitional acceptance of the pre-care-team ownership signal: the episode,
     * patient or run {@code created_by} email text.
     *
     * <p>
     * The V36 migration backfilled a care-team OWNER row for every resolvable
     * episode creator and the episode creation paths now enroll the creator at
     * creation time, so this branch exists for records written between the
     * migration and the enrollment rollout. It is deliberately narrow: it grants
     * access only to the exact caller the legacy audit column names, and never to
     * an anonymous or blank owner.
     */
    private boolean isResolvableLegacyCreator(ActivePrincipal principal, PjiEpisode episode, AiRecommendationRun run) {
        Set<String> ownerEmails = new LinkedHashSet<>();
        ownerEmails.add(episode.getCreatedBy());
        if (episode.getPatient() != null) {
            ownerEmails.add(episode.getPatient().getCreatedBy());
        }
        if (run != null) {
            // run.createdByUserId is a real FK; it is the authoritative signal when
            // the run predates the care team and was created by an authenticated user.
            if (principal.is(run.getCreatedByUserId())) {
                return true;
            }
            ownerEmails.add(run.getCreatedBy());
        }

        if (principal.email() == null) {
            return false;
        }
        for (String ownerEmail : ownerEmails) {
            if (sameUser(principal.email(), ownerEmail)) {
                return true;
            }
        }
        return false;
    }

    /** Mirrors the historical case-insensitive ownership comparison. */
    private boolean sameUser(String currentEmail, String ownerEmail) {
        if (ownerEmail == null || ownerEmail.isBlank() || currentEmail == null) {
            return false;
        }
        return currentEmail.trim().equalsIgnoreCase(ownerEmail.trim());
    }
}
