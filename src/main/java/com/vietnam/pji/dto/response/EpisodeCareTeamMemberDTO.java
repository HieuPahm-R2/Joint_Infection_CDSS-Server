package com.vietnam.pji.dto.response;

import com.vietnam.pji.constant.CareTeamMemberRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One care-team membership of an episode.
 *
 * <p>
 * A DTO rather than the entity: serializing {@code EpisodeCareTeam} directly
 * would drag the lazily loaded {@code episode} and {@code user} associations into
 * Jackson after the Hibernate session closed, which is exactly the failure the
 * codebase's own {@code AiRecommendationRunDTO} documents.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeCareTeamMemberDTO {

    private Long id;
    private Long userId;
    private String email;
    private String fullName;
    private String department;
    private CareTeamMemberRole memberRole;
    private boolean active;
}
