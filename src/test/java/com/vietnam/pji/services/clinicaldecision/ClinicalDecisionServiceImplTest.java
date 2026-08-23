package com.vietnam.pji.services.clinicaldecision;

import com.vietnam.pji.constant.ClinicalDecisionStatus;
import com.vietnam.pji.constant.RunStatus;
import com.vietnam.pji.dto.request.DoctorClinicalDecisionRequestDTO;
import com.vietnam.pji.dto.request.PharmacistClinicalDecisionRequestDTO;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.agentic.DoctorFinalDecision;
import com.vietnam.pji.model.agentic.PharmacistFinalDecision;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.DoctorFinalDecisionRepository;
import com.vietnam.pji.repository.DoctorRecommendationReviewRepository;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.PharmacistFinalDecisionRepository;
import com.vietnam.pji.repository.RecommendationFinalSelectionRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.agent.RecommendationAccessService;
import com.vietnam.pji.services.auth.UserService;
import com.vietnam.pji.services.clinicaldecision.impl.ClinicalDecisionServiceImpl;
import com.vietnam.pji.utils.SecurityUtils;
import com.vietnam.pji.utils.mapper.AiRecommendationRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClinicalDecisionServiceImplTest {

    private final AiRecommendationRunRepository runRepository = mock(AiRecommendationRunRepository.class);
    private final EpisodeRepository episodeRepository = mock(EpisodeRepository.class);
    private final DoctorRecommendationReviewRepository reviewRepository = mock(DoctorRecommendationReviewRepository.class);
    private final DoctorFinalDecisionRepository doctorDecisionRepository = mock(DoctorFinalDecisionRepository.class);
    private final PharmacistFinalDecisionRepository pharmacistDecisionRepository = mock(PharmacistFinalDecisionRepository.class);
    private final RecommendationFinalSelectionRepository finalSelectionRepository = mock(RecommendationFinalSelectionRepository.class);
    private final RecommendationAccessService accessService = mock(RecommendationAccessService.class);
    private final UserService userService = mock(UserService.class);
    private final AiRecommendationRunMapper runMapper = mock(AiRecommendationRunMapper.class);

    private ClinicalDecisionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClinicalDecisionServiceImpl(
                runRepository,
                episodeRepository,
                reviewRepository,
                doctorDecisionRepository,
                pharmacistDecisionRepository,
                finalSelectionRepository,
                accessService,
                userService,
                runMapper);
    }

    @Test
    void anotherDoctorCannotWriteTheRunOwnersDecision() {
        AiRecommendationRun run = run(7L, 11L);
        User otherDoctor = user(22L, "other.doctor@example.test", "DOCTOR");
        when(runRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(run));
        when(userService.handleGetUserByUsername(otherDoctor.getEmail())).thenReturn(otherDoctor);

        DoctorClinicalDecisionRequestDTO request = new DoctorClinicalDecisionRequestDTO();
        request.setRevision(0L);

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.of(otherDoctor.getEmail()));

            assertThatThrownBy(() -> service.saveDoctorDecision(7L, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("created this AI run");
        }
    }

    @Test
    void secondPharmacistCannotOverwriteClaimedDraft() {
        AiRecommendationRun run = run(7L, 11L);
        User owner = user(31L, "owner.pharmacist@example.test", "PHARMACIST");
        User other = user(32L, "other.pharmacist@example.test", "PHARMACIST");
        PharmacistFinalDecision existing = PharmacistFinalDecision.builder()
                .run(run)
                .author(owner)
                .status(ClinicalDecisionStatus.DRAFT)
                .version(0L)
                .build();
        when(runRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(run));
        when(pharmacistDecisionRepository.findByRunId(7L)).thenReturn(Optional.of(existing));
        when(userService.handleGetUserByUsername(other.getEmail())).thenReturn(other);

        PharmacistClinicalDecisionRequestDTO request = new PharmacistClinicalDecisionRequestDTO();
        request.setRevision(0L);

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.of(other.getEmail()));

            assertThatThrownBy(() -> service.savePharmacistDecision(7L, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("another user");
        }
    }

    @Test
    void finalRunRequiresBothSignedDecisions() {
        AiRecommendationRun run = run(7L, 11L);
        User doctor = user(11L, "doctor@example.test", "DOCTOR");
        DoctorFinalDecision doctorDraft = DoctorFinalDecision.builder()
                .run(run)
                .author(doctor)
                .status(ClinicalDecisionStatus.DRAFT)
                .version(0L)
                .build();
        PharmacistFinalDecision pharmacistSigned = PharmacistFinalDecision.builder()
                .run(run)
                .author(user(31L, "pharmacist@example.test", "PHARMACIST"))
                .status(ClinicalDecisionStatus.SIGNED)
                .version(0L)
                .build();
        when(runRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(run));
        when(doctorDecisionRepository.findByRunId(7L)).thenReturn(Optional.of(doctorDraft));
        when(pharmacistDecisionRepository.findByRunId(7L)).thenReturn(Optional.of(pharmacistSigned));
        when(userService.handleGetUserByUsername(doctor.getEmail())).thenReturn(doctor);

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.of(doctor.getEmail()));

            assertThatThrownBy(() -> service.selectFinalRun(91L, 7L))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("Both doctor and pharmacist decisions must be signed");
        }
    }

    private AiRecommendationRun run(Long runId, Long ownerUserId) {
        PjiEpisode episode = PjiEpisode.builder().build();
        episode.setId(91L);
        AiRecommendationRun run = AiRecommendationRun.builder()
                .episode(episode)
                .createdByUserId(ownerUserId)
                .status(RunStatus.SUCCESS)
                .build();
        run.setId(runId);
        return run;
    }

    private User user(Long id, String email, String roleName) {
        User user = User.builder()
                .email(email)
                .role(new Role(roleName, roleName, true, null, null))
                .build();
        user.setId(id);
        return user;
    }
}
