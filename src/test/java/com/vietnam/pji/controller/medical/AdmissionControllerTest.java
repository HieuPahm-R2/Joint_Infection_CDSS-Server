package com.vietnam.pji.controller.medical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.dto.request.AdmissionFullRequestDTO;
import com.vietnam.pji.dto.response.AdmissionFullResponseDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.services.episode.AdmissionAggregateService;
import com.vietnam.pji.services.episode.AdmissionService;
import com.vietnam.pji.services.episode.AdmissionLockService;
import com.vietnam.pji.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdmissionControllerTest {

    @Test
    void updateFullReturnsOnlyStatusAndMessage() throws Exception {
        AdmissionFullRequestDTO request = new AdmissionFullRequestDTO();
        AtomicBoolean updateCalled = new AtomicBoolean();
        AdmissionAggregateService episodeAggregateService = new AdmissionAggregateService() {
            @Override
            public AdmissionFullResponseDTO getFull(Long episodeId) {
                throw new AssertionError("PUT must not rebuild the full aggregate");
            }

            @Override
            public AdmissionFullResponseDTO saveFull(Long episodeId, AdmissionFullRequestDTO dto) {
                throw new AssertionError("PUT must not use the full-response save path");
            }

            @Override
            public void updateFull(Long episodeId, AdmissionFullRequestDTO dto) {
                assertThat(episodeId).isEqualTo(11L);
                assertThat(dto).isSameAs(request);
                updateCalled.set(true);
            }
        };
        AdmissionService episodeService = (AdmissionService) Proxy.newProxyInstance(
                AdmissionService.class.getClassLoader(),
                new Class<?>[] { AdmissionService.class },
                (proxy, method, args) -> {
                    throw new AssertionError("AdmissionService should not be called directly");
                });
        AdmissionLockService episodeLockService = mock(AdmissionLockService.class);
        AdmissionController controller = new AdmissionController(episodeService, episodeAggregateService, episodeLockService);

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
