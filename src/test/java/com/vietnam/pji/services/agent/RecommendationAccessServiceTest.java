package com.vietnam.pji.services.agent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.model.medical.Patient;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.services.auth.UserService;
import com.vietnam.pji.utils.SecurityUtils;

class RecommendationAccessServiceTest {

    private final EpisodeRepository episodeRepository = mock(EpisodeRepository.class);
    private final AiRecommendationRunRepository runRepository = mock(AiRecommendationRunRepository.class);
    private final UserService userService = mock(UserService.class);
    private final RecommendationAccessService service = new RecommendationAccessService(
            episodeRepository,
            runRepository,
            userService);

    @Test
    void runOwnerCanReadRecommendation() {
        AiRecommendationRun run = run("doctor@example.test");
        when(runRepository.findById(7L)).thenReturn(Optional.of(run));

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin)
                    .thenReturn(Optional.of("doctor@example.test"));

            assertThatCode(() -> service.assertCanAccessRun(7L)).doesNotThrowAnyException();
        }
    }

    @Test
    void anotherUserCannotReadRecommendation() {
        AiRecommendationRun run = run("owner@example.test");
        when(runRepository.findById(7L)).thenReturn(Optional.of(run));

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin)
                    .thenReturn(Optional.of("other@example.test"));

            assertThatThrownBy(() -> service.assertCanAccessRun(7L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("owner");
        }
    }

    @Test
    void adminCanReadAnotherUsersRecommendation() {
        AiRecommendationRun run = run("owner@example.test");
        when(runRepository.findById(7L)).thenReturn(Optional.of(run));
        User admin = User.builder().role(new Role("ADMIN", "", true, null, null)).build();
        when(userService.handleGetUserByUsername("admin@example.test")).thenReturn(admin);

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin)
                    .thenReturn(Optional.of("admin@example.test"));

            assertThatCode(() -> service.assertCanAccessRun(7L)).doesNotThrowAnyException();
        }
    }

    @Test
    void anotherUserCannotAccessEpisodeRecommendationHistory() {
        PjiEpisode episode = run("owner@example.test").getEpisode();
        when(episodeRepository.findById(9L)).thenReturn(Optional.of(episode));

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin)
                    .thenReturn(Optional.of("other@example.test"));

            assertThatThrownBy(() -> service.assertCanAccessEpisode(9L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("owner");
        }
    }

    @Test
    void unauthenticatedRequestCannotReadRecommendation() {
        when(runRepository.findById(7L)).thenReturn(Optional.of(run("owner@example.test")));

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assertCanAccessRun(7L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("permission");
        }
    }

    private AiRecommendationRun run(String owner) {
        Patient patient = Patient.builder().fullName("Patient").build();
        patient.setCreatedBy(owner);
        PjiEpisode episode = PjiEpisode.builder().patient(patient).build();
        episode.setCreatedBy(owner);
        AiRecommendationRun run = AiRecommendationRun.builder().episode(episode).build();
        run.setCreatedBy(owner);
        return run;
    }
}
