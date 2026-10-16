package com.vietnam.pji.services.agent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.auth.ActivePrincipal;
import com.vietnam.pji.services.auth.CurrentActorProvider;
import com.vietnam.pji.services.auth.UserService;
import com.vietnam.pji.services.episode.EpisodeRecordAccess;
import com.vietnam.pji.utils.SecurityUtils;

/**
 * Pins the two cumulative authorization layers for the recommendation workflows:
 * the scope's professional lane, and the record relationship.
 *
 * <p>
 * The regression this class must prevent: the previous implementation returned
 * early for every pharmacist, so a pharmacist holding the route permission could
 * generate an antibiotic recommendation against any patient's episode, while an
 * administrator was checked more strictly than a pharmacist.
 */
class RecommendationAccessServiceTest {

    private final EpisodeRepository episodeRepository = mock(EpisodeRepository.class);
    private final AiRecommendationRunRepository runRepository = mock(AiRecommendationRunRepository.class);
    private final UserService userService = mock(UserService.class);
    private final EpisodeRecordAccess recordAccess = mock(EpisodeRecordAccess.class);

    private final RecommendationAccessService service = new RecommendationAccessService(
            episodeRepository,
            runRepository,
            new CurrentActorProvider(userService),
            recordAccess);

    @Test
    void pharmacistGeneratingAntibioticRunStillHasToReachTheRecord() {
        PjiEpisode episode = episode(9L, "doctor@example.test");
        when(episodeRepository.findById(9L)).thenReturn(Optional.of(episode));
        // The record check is what used to be skipped for this lane.
        doThrow(new ForbiddenException("not on the care team"))
                .when(recordAccess).assertCanAccess(any(), eq(episode), isNull());

        as("pharmacist@example.test", "PHARMACIST", user -> {
            assertThatThrownBy(() -> service.assertCanGenerateEpisode(9L, RecommendationScope.ANTIBIOTIC))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("care team");
        });
    }

    @Test
    void pharmacistGeneratingForAnEpisodeTheyBelongToIsAllowed() {
        PjiEpisode episode = episode(9L, "doctor@example.test");
        when(episodeRepository.findById(9L)).thenReturn(Optional.of(episode));

        as("pharmacist@example.test", "PHARMACIST", user -> {
            assertThatCode(() -> service.assertCanGenerateEpisode(9L, RecommendationScope.ANTIBIOTIC))
                    .doesNotThrowAnyException();
        });
        verify(recordAccess).assertCanAccess(any(), eq(episode), isNull());
    }

    @Test
    void pharmacistCannotGenerateASurgeryRun() {
        PjiEpisode episode = episode(9L, "doctor@example.test");
        when(episodeRepository.findById(9L)).thenReturn(Optional.of(episode));

        as("pharmacist@example.test", "PHARMACIST", user -> {
            assertThatThrownBy(() -> service.assertCanGenerateEpisode(9L, RecommendationScope.SURGERY))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Only doctors");
        });
        // The lane check runs before any record work.
        verify(recordAccess, never()).assertCanAccess(any(), any(), any());
    }

    @Test
    void doctorCannotGenerateAnAntibioticRun() {
        when(episodeRepository.findById(9L)).thenReturn(Optional.of(episode(9L, "doctor@example.test")));

        as("doctor@example.test", "DOCTOR", user -> {
            assertThatThrownBy(() -> service.assertCanGenerateEpisode(9L, RecommendationScope.ANTIBIOTIC))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Only pharmacists");
        });
    }

    @Test
    void missingScopeIsRejectedBeforeAnyLookup() {
        as("doctor@example.test", "DOCTOR", user -> {
            assertThatThrownBy(() -> service.assertCanGenerateEpisode(9L, null))
                    .isInstanceOf(InvalidDataException.class);
        });
        verify(episodeRepository, never()).findById(any());
    }

    @Test
    void deactivatedAccountCannotGenerate() {
        when(episodeRepository.findById(9L)).thenReturn(Optional.of(episode(9L, "doctor@example.test")));

        User inactive = user("doctor@example.test", "DOCTOR");
        inactive.setStatus(com.vietnam.pji.constant.UserStatus.INACTIVE);
        when(userService.handleGetUserByUsername("doctor@example.test")).thenReturn(inactive);

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.of("doctor@example.test"));

            assertThatThrownBy(() -> service.assertCanGenerateEpisode(9L, RecommendationScope.SURGERY))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not active");
        }
    }

    @Test
    void disabledRoleCannotGenerate() {
        when(episodeRepository.findById(9L)).thenReturn(Optional.of(episode(9L, "doctor@example.test")));

        User user = User.builder().email("doctor@example.test").build();
        user.setId(11L);
        user.setStatus(com.vietnam.pji.constant.UserStatus.ACTIVE);
        user.setRole(new Role("DOCTOR", "", false, null, null));
        when(userService.handleGetUserByUsername("doctor@example.test")).thenReturn(user);

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.of("doctor@example.test"));

            assertThatThrownBy(() -> service.assertCanGenerateEpisode(9L, RecommendationScope.SURGERY))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("active role");
        }
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        when(runRepository.findById(7L)).thenReturn(Optional.of(run("owner@example.test")));

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assertCanAccessRun(7L))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void runAccessAlsoRequiresTheRecordRelationship() {
        AiRecommendationRun run = run("owner@example.test");
        when(runRepository.findById(7L)).thenReturn(Optional.of(run));
        doThrow(new ForbiddenException("not on the care team"))
                .when(recordAccess).assertCanAccess(any(), eq(run.getEpisode()), eq(run));

        as("other@example.test", "DOCTOR", user -> {
            assertThatThrownBy(() -> service.assertCanAccessRun(7L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("care team");
        });
    }

    /** Runs {@code assertion} as an ACTIVE user with {@code roleName}. */
    private void as(String email, String roleName, java.util.function.Consumer<User> assertion) {
        when(userService.handleGetUserByUsername(email)).thenReturn(user(email, roleName));
        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.of(email));
            assertion.accept(user(email, roleName));
        }
    }

    private User user(String email, String roleName) {
        User user = User.builder().email(email).build();
        user.setId(11L);
        user.setStatus(com.vietnam.pji.constant.UserStatus.ACTIVE);
        user.setRole(new Role(roleName, "", true, null, null));
        return user;
    }

    private PjiEpisode episode(Long id, String owner) {
        PjiEpisode episode = PjiEpisode.builder().build();
        episode.setId(id);
        episode.setCreatedBy(owner);
        return episode;
    }

    private AiRecommendationRun run(String owner) {
        PjiEpisode episode = episode(9L, owner);
        AiRecommendationRun run = AiRecommendationRun.builder().episode(episode).build();
        run.setId(7L);
        run.setCreatedBy(owner);
        return run;
    }
}
