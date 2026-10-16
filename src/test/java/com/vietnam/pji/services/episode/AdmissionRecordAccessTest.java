package com.vietnam.pji.services.episode;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.medical.Patient;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.repository.medical.AdmissionCareTeamRepository;
import com.vietnam.pji.services.auth.ActivePrincipal;

/**
 * Pins the record-relationship decision from
 * {@code docs/decisions/0008-general-authorization-policy.md}.
 *
 * <p>
 * The regression this class exists for: the previous check granted access
 * whenever the free-text owner metadata was blank, and
 * {@code AbstractEntity.handleBeforeCreate} writes the literal {@code " "} when
 * there is no authenticated principal - so every record persisted outside a
 * request was readable by any caller. Absence of ownership evidence must now be a
 * denial for non-administrators.
 */
class AdmissionRecordAccessTest {

    private static final ActivePrincipal DOCTOR = new ActivePrincipal(11L, "doctor@example.test", "DOCTOR", false);
    private static final ActivePrincipal ADMIN = new ActivePrincipal(99L, "admin@example.test", "ADMIN", true);

    private final AdmissionCareTeamRepository careTeamRepository = mock(AdmissionCareTeamRepository.class);
    private final AdmissionRecordAccess access = new AdmissionRecordAccess(careTeamRepository);

    @Test
    void careTeamMemberCanAccess() {
        when(careTeamRepository.existsActiveMember(9L, DOCTOR.userId())).thenReturn(true);

        assertThatCode(() -> access.assertCanAccess(DOCTOR, episode("someone-else@example.test"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void revokedMemberCannotAccess() {
        // A revoked membership has active = false, so the repository reports false.
        when(careTeamRepository.existsActiveMember(9L, DOCTOR.userId())).thenReturn(false);

        assertThatThrownBy(() -> access.assertCanAccess(DOCTOR, episode("owner@example.test"), null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("care team");
    }

    @Test
    void blankOwnerMetadataDeniesNonAdmin() {
        when(careTeamRepository.existsActiveMember(9L, DOCTOR.userId())).thenReturn(false);

        // AbstractEntity writes " " when there is no principal; null is the other
        // shape the old branch treated as "no owner metadata, therefore allowed".
        assertThatThrownBy(() -> access.assertCanAccess(DOCTOR, episode(" "), null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> access.assertCanAccess(DOCTOR, episode(null), null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> access.assertCanAccess(DOCTOR, episode(""), null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void legacyCreatorEmailStillReachesTheirRecord() {
        when(careTeamRepository.existsActiveMember(9L, DOCTOR.userId())).thenReturn(false);

        // Transitional: records written between the V36 backfill and the
        // enrolment rollout are still reachable by the creator the audit column
        // names, case-insensitively as the historical comparison did.
        assertThatCode(() -> access.assertCanAccess(DOCTOR, episode("Doctor@Example.Test"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void patientCreatorIsAlsoAcceptedAsLegacyOwner() {
        when(careTeamRepository.existsActiveMember(9L, DOCTOR.userId())).thenReturn(false);
        Admission episode = Admission.builder()
                .patient(Patient.builder().build())
                .build();
        episode.setId(9L);
        episode.getPatient().setCreatedBy("doctor@example.test");

        assertThatCode(() -> access.assertCanAccess(DOCTOR, episode, null)).doesNotThrowAnyException();
    }

    @Test
    void runCreatorUserIdIsAuthoritativeWhenNoEmailMatches() {
        when(careTeamRepository.existsActiveMember(9L, DOCTOR.userId())).thenReturn(false);
        AiRecommendationRun run = AiRecommendationRun.builder().build();
        run.setId(7L);
        run.setCreatedByUserId(DOCTOR.userId());

        assertThatCode(() -> access.assertCanAccess(DOCTOR, episode("nobody@example.test"), run))
                .doesNotThrowAnyException();
    }

    @Test
    void anotherUserCannotAccess() {
        when(careTeamRepository.existsActiveMember(9L, DOCTOR.userId())).thenReturn(false);

        assertThatThrownBy(() -> access.assertCanAccess(DOCTOR, episode("owner@example.test"), null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("care team");
    }

    @Test
    void activeAdministratorBreaksGlass() {
        // No care-team row and no ownership metadata at all.
        when(careTeamRepository.existsActiveMember(anyLong(), anyLong())).thenReturn(false);

        assertThatCode(() -> access.assertCanAccess(ADMIN, episode(" "), null)).doesNotThrowAnyException();
    }

    @Test
    void inactiveAdministratorIsResolvedAsAnOrdinaryUser() {
        // CurrentActorProvider only reports admin=true for an ACTIVE account with an
        // active role, so a deactivated administrator arrives here as a non-admin and
        // must be denied like anyone else.
        ActivePrincipal demoted = new ActivePrincipal(99L, "admin@example.test", "ADMIN", false);
        when(careTeamRepository.existsActiveMember(anyLong(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> access.assertCanAccess(demoted, episode("owner@example.test"), null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void nullPrincipalOrEpisodeDenies() {
        assertThatThrownBy(() -> access.assertCanAccess(null, episode("owner@example.test"), null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> access.assertCanAccess(DOCTOR, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    private Admission episode(String createdBy) {
        Admission episode = Admission.builder().build();
        episode.setId(9L);
        episode.setCreatedBy(createdBy);
        return episode;
    }
}
