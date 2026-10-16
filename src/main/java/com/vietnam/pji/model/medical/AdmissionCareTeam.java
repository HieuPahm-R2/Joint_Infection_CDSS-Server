package com.vietnam.pji.model.medical;

import com.vietnam.pji.constant.CareTeamMemberRole;
import com.vietnam.pji.model.AbstractEntity;
import com.vietnam.pji.model.auth.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Explicit, revocable membership of a user on an episode's care team.
 *
 * <p>
 * This is the record-relationship layer of
 * {@code docs/decisions/0008-general-authorization-policy.md}: it is what lets a
 * pharmacist reach an episode they did not create, without the blanket
 * role-wide exemption it replaces, and what makes "who could see this record"
 * answerable and auditable.
 *
 * <p>
 * Revoking access sets {@link #active} to {@code false} rather than deleting the
 * row, so the history of responsibility survives.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "episode_care_team")
public class AdmissionCareTeam extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Admission episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 32)
    private CareTeamMemberRole memberRole;

    /** {@code false} means the membership was revoked and grants nothing. */
    @Column(name = "active", nullable = false)
    private boolean active;

    /** @return whether this membership is the episode's responsible clinician */
    public boolean isOwner() {
        return active && memberRole == CareTeamMemberRole.OWNER;
    }
}
