package com.vietnam.pji.services.episode;

import java.util.List;

import com.vietnam.pji.constant.CareTeamMemberRole;
import com.vietnam.pji.dto.response.EpisodeCareTeamMemberDTO;
import com.vietnam.pji.model.medical.PjiEpisode;

/**
 * Granting, listing and revoking access to an episode's clinical record.
 *
 * <p>
 * Membership is the record-relationship layer of
 * {@code docs/decisions/0008-general-authorization-policy.md}. It exists so that
 * access to a clinical record can be granted to a colleague - most importantly a
 * pharmacist, who does not create episodes - as an explicit, revocable act
 * instead of a blanket role-wide exemption.
 */
public interface EpisodeCareTeamService {

    /** Members that currently hold access, owners first. Requires access. */
    List<EpisodeCareTeamMemberDTO> listMembers(Long episodeId);

    /**
     * Grants access (or re-activates a revoked grant) for a user. Requires an
     * active principal that is the episode's OWNER or an administrator.
     *
     * @throws com.vietnam.pji.exception.ForbiddenException when the caller may not
     *                                                      administer this team
     * @throws com.vietnam.pji.exception.ResourceNotFoundException when the episode
     *                                                      or user does not exist
     * @throws com.vietnam.pji.exception.InvalidDataException when the role is
     *                                                      unknown or the caller
     *                                                      tries to create a
     *                                                      second active owner
     */
    EpisodeCareTeamMemberDTO assignMember(Long episodeId, Long userId, CareTeamMemberRole memberRole);

    /**
     * Revokes a member's access. Requires an active principal that is the
     * episode's OWNER or an administrator. The episode's last active owner cannot
     * be revoked, so an episode is never left unadministrable.
     *
     * @throws com.vietnam.pji.exception.ForbiddenException when the caller may not
     *                                                      administer this team
     */
    void revokeMember(Long episodeId, Long userId);

    /**
     * Records {@code userId} as the episode's OWNER.
     *
     * <p>
     * System-internal: called when an authenticated user creates an episode, so
     * the creator owns it from the first request. It is intentionally not exposed
     * through a controller, and it performs no authorization of its own because
     * the caller is the creation path. Idempotent.
     */
    void enrollOwner(PjiEpisode episode, Long userId);
}
