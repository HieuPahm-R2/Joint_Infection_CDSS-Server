package com.vietnam.pji.constant;

import java.util.Locale;

/**
 * Membership role of a user on an episode's care team.
 *
 * <p>
 * This is authorization metadata for
 * {@code episode_care_team.member_role}, not a clinical enum: it records why a
 * user was granted access to the episode, and it is what makes a pharmacist's
 * access explicit instead of a blanket "any pharmacist may act on any episode"
 * exemption.
 *
 * <p>
 * The stored value is the plain name (see V36); {@link #from(String)} is the only
 * supported way to parse a client-supplied value, and it rejects anything
 * unknown rather than guessing.
 */
public enum CareTeamMemberRole {

    /** The clinician responsible for the episode; may grant and revoke access. */
    OWNER,

    /** A doctor participating in the episode's care. */
    DOCTOR,

    /** A pharmacist participating in the episode's care. */
    PHARMACIST,

    /** A nurse participating in the episode's care. */
    NURSE;

    /** @return the role for a client-supplied value, or {@code null} if unknown */
    public static CareTeamMemberRole from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
