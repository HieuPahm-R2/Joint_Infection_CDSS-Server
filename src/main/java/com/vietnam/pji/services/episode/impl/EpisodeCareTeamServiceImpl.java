package com.vietnam.pji.services.episode.impl;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietnam.pji.constant.CareTeamMemberRole;
import com.vietnam.pji.dto.response.EpisodeCareTeamMemberDTO;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.model.medical.EpisodeCareTeam;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.UserRepository;
import com.vietnam.pji.repository.medical.EpisodeCareTeamRepository;
import com.vietnam.pji.services.auth.ActivePrincipal;
import com.vietnam.pji.services.auth.CurrentActorProvider;
import com.vietnam.pji.services.episode.EpisodeCareTeamService;
import com.vietnam.pji.services.episode.EpisodeRecordAccess;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Care-team membership administration.
 *
 * <p>
 * Membership is what makes a clinical record reachable for a colleague who did
 * not create it - notably a pharmacist, who never creates an episode. Before
 * this existed, the only way to express that was
 * {@code RecommendationAccessService}'s blanket "any pharmacist may generate on
 * any episode" exemption, which granted every pharmacist access to every
 * patient. Access is now an explicit, revocable grant on one episode.
 *
 * <p>
 * Authorisation is deliberately split:
 *
 * <ul>
 * <li>{@link #assignMember} and {@link #revokeMember} require the caller to be the
 * episode's OWNER or an active administrator, checked through
 * {@link EpisodeRecordAccess} so a revoked member cannot re-grant themselves.</li>
 * <li>{@link #enrollOwner} performs no check because its caller is the episode
 * creation path, which has not yet recorded an owner.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EpisodeCareTeamServiceImpl implements EpisodeCareTeamService {

    private final EpisodeCareTeamRepository careTeamRepository;
    private final UserRepository userRepository;
    private final CurrentActorProvider actorProvider;

    @Override
    @Transactional(readOnly = true)
    public List<EpisodeCareTeamMemberDTO> listMembers(Long episodeId) {
        ActivePrincipal principal = actorProvider.require();
        if (!principal.admin()
                && !careTeamRepository.existsActiveMember(episodeId, principal.userId())) {
            throw new ForbiddenException("Only a member of this episode's care team can view its care team");
        }
        return careTeamRepository.findByEpisodeIdAndActiveTrue(episodeId).stream()
                .sorted(Comparator
                        .comparing((EpisodeCareTeam member) -> member.getMemberRole() != CareTeamMemberRole.OWNER)
                        .thenComparing(EpisodeCareTeam::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public EpisodeCareTeamMemberDTO assignMember(Long episodeId, Long userId, CareTeamMemberRole memberRole) {
        if (memberRole == null) {
            throw new InvalidDataException("Unknown care-team role. Expected OWNER, DOCTOR, PHARMACIST or NURSE");
        }
        ActivePrincipal principal = actorProvider.require();
        PjiEpisode episode = requireEpisodeInTeam(episodeId, principal);

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (memberRole == CareTeamMemberRole.OWNER) {
            requireNoOtherActiveOwner(episodeId, userId);
        }

        EpisodeCareTeam member = careTeamRepository.findByEpisodeIdAndUserId(episodeId, userId)
                .orElseGet(() -> EpisodeCareTeam.builder()
                        .episode(episode)
                        .user(target)
                        .build());
        member.setMemberRole(memberRole);
        member.setActive(true);
        EpisodeCareTeam saved = careTeamRepository.save(member);

        log.info("SECURITY_AUDIT care_team_assigned episodeId={} targetUserId={} role={} byUserId={}",
                episodeId, userId, memberRole, principal.userId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public void revokeMember(Long episodeId, Long userId) {
        ActivePrincipal principal = actorProvider.require();
        requireEpisodeInTeam(episodeId, principal);

        EpisodeCareTeam member = careTeamRepository.findByEpisodeIdAndUserId(episodeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("This user is not on the episode's care team"));

        if (member.isOwner() && countActiveOwners(episodeId) <= 1) {
            throw new InvalidDataException(
                    "The episode's only owner cannot be removed. Assign another owner first.");
        }

        member.setActive(false);
        careTeamRepository.save(member);

        log.info("SECURITY_AUDIT care_team_revoked episodeId={} targetUserId={} byUserId={}",
                episodeId, userId, principal.userId());
    }

    @Override
    @Transactional
    public void enrollOwner(PjiEpisode episode, Long userId) {
        if (episode == null || episode.getId() == null || userId == null) {
            return;
        }
        careTeamRepository.findByEpisodeIdAndUserId(episode.getId(), userId).ifPresentOrElse(
                existing -> {
                    if (!existing.isOwner()) {
                        existing.setMemberRole(CareTeamMemberRole.OWNER);
                        existing.setActive(true);
                        careTeamRepository.save(existing);
                    }
                },
                () -> {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user == null) {
                        log.warn("Cannot enroll episode owner: no user row for id={}", userId);
                        return;
                    }
                    // Only the first owner claim may create an active OWNER row; the
                    // partial unique index uq_episode_care_team_active_owner enforces it
                    // at the database too, so a race cannot produce two owners.
                    if (careTeamRepository.existsActiveMemberWithRole(episode.getId(), CareTeamMemberRole.OWNER)) {
                        log.info("Episode already has an active owner; enrolling userId={} as DOCTOR", userId);
                        careTeamRepository.save(EpisodeCareTeam.builder()
                                .episode(episode)
                                .user(user)
                                .memberRole(CareTeamMemberRole.DOCTOR)
                                .active(true)
                                .build());
                        return;
                    }
                    careTeamRepository.save(EpisodeCareTeam.builder()
                            .episode(episode)
                            .user(user)
                            .memberRole(CareTeamMemberRole.OWNER)
                            .active(true)
                            .build());
                });
    }

    /**
     * Loads the episode and requires the caller to be its OWNER or an active
     * administrator.
     */
    private PjiEpisode requireEpisodeInTeam(Long episodeId, ActivePrincipal principal) {
        if (!principal.admin()) {
            EpisodeCareTeam membership = careTeamRepository.findByEpisodeIdAndUserId(episodeId, principal.userId())
                    .filter(EpisodeCareTeam::isOwner)
                    .orElseThrow(() -> new ForbiddenException(
                            "Only the episode's owner can change its care team"));
            return membership.getEpisode();
        }
        EpisodeCareTeam anyMembership = careTeamRepository.findByEpisodeIdAndUserId(episodeId, principal.userId())
                .orElse(null);
        if (anyMembership != null) {
            return anyMembership.getEpisode();
        }
        throw new ResourceNotFoundException("Episode not found or has no care team: " + episodeId);
    }

    private void requireNoOtherActiveOwner(Long episodeId, Long userId) {
        careTeamRepository.findByEpisodeIdAndActiveTrue(episodeId).stream()
                .filter(EpisodeCareTeam::isOwner)
                .filter(member -> !userId.equals(member.getUser().getId()))
                .findFirst()
                .ifPresent(member -> {
                    throw new InvalidDataException("This episode already has an active owner");
                });
    }

    private long countActiveOwners(Long episodeId) {
        return careTeamRepository.findByEpisodeIdAndActiveTrue(episodeId).stream()
                .filter(EpisodeCareTeam::isOwner)
                .count();
    }

    private EpisodeCareTeamMemberDTO toDto(EpisodeCareTeam member) {
        User user = member.getUser();
        return EpisodeCareTeamMemberDTO.builder()
                .id(member.getId())
                .userId(user == null ? null : user.getId())
                .email(user == null ? null : user.getEmail())
                .fullName(user == null ? null : user.getFullName())
                .department(user == null ? null : user.getDepartment())
                .memberRole(member.getMemberRole())
                .active(member.isActive())
                .build();
    }
}
