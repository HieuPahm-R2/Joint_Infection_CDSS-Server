package com.vietnam.pji.repository.medical;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vietnam.pji.constant.CareTeamMemberRole;
import com.vietnam.pji.model.medical.AdmissionCareTeam;

/**
 * Care-team membership of an episode.
 *
 * <p>
 * Every query filters on {@code active = true}: a revoked membership must grant
 * nothing. The check used on the authorization hot path is
 * {@link #existsActiveMember(Long, Long)}, a single indexed lookup on
 * {@code uq_episode_care_team_episode_user}.
 */
@Repository
public interface AdmissionCareTeamRepository extends JpaRepository<AdmissionCareTeam, Long> {

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM AdmissionCareTeam m
            WHERE m.episode.id = :episodeId AND m.user.id = :userId AND m.active = true
            """)
    boolean existsActiveMember(@Param("episodeId") Long episodeId, @Param("userId") Long userId);

    Optional<AdmissionCareTeam> findByEpisodeIdAndUserId(Long episodeId, Long userId);

    List<AdmissionCareTeam> findByEpisodeIdAndActiveTrue(Long episodeId);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM AdmissionCareTeam m
            WHERE m.episode.id = :episodeId AND m.active = true AND m.memberRole = :memberRole
            """)
    boolean existsActiveMemberWithRole(@Param("episodeId") Long episodeId,
            @Param("memberRole") CareTeamMemberRole memberRole);
}
