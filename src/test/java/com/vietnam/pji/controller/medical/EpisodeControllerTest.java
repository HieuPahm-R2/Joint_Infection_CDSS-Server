package com.vietnam.pji.controller.medical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.dto.request.EpisodeFullRequestDTO;
import com.vietnam.pji.dto.response.EpisodeFullResponseDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.services.episode.EpisodeAggregateService;
import com.vietnam.pji.services.episode.EpisodeService;
import com.vietnam.pji.services.episode.EpisodeLockService;
import com.vietnam.pji.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EpisodeControllerTest {

    @Test
    void updateFullReturnsOnlyStatusAndMessage() throws Exception {
        EpisodeFullRequestDTO request = new EpisodeFullRequestDTO();
        AtomicBoolean updateCalled = new AtomicBoolean();
        EpisodeAggregateService episodeAggregateService = new EpisodeAggregateService() {
            @Override
            public EpisodeFullResponseDTO getFull(Long episodeId) {
                throw new AssertionError("PUT must not rebuild the full aggregate");
            }

            @Override
            public EpisodeFullResponseDTO saveFull(Long episodeId, EpisodeFullRequestDTO dto) {
                throw new AssertionError("PUT must not use the full-response save path");
            }

            @Override
            public void updateFull(Long episodeId, EpisodeFullRequestDTO dto) {
                assertThat(episodeId).isEqualTo(11L);
                assertThat(dto).isSameAs(request);
                updateCalled.set(true);
            }
        };
        EpisodeService episodeService = (EpisodeService) Proxy.newProxyInstance(
                EpisodeService.class.getClassLoader(),
                new Class<?>[] { EpisodeService.class },
                (proxy, method, args) -> {
                    throw new AssertionError("EpisodeService should not be called directly");
                });
        EpisodeLockService episodeLockService = mock(EpisodeLockService.class);
        EpisodeController controller = new EpisodeController(episodeService, episodeAggregateService, episodeLockService);

        ResponseData<Void> response;
        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            response = controller.updateEpisodeFull(11L, request);
        }

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("Episode updated successfully");
        assertThat(response.getData()).isNull();
        assertThat(new ObjectMapper().writeValueAsString(response)).doesNotContain("\"data\"");
        assertThat(updateCalled).isTrue();
        verify(episodeLockService).assertHeldBy(11L, 7L);
    }
}
