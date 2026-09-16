package com.vietnam.pji.services.episode.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.model.medical.CultureResult;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.model.medical.SensitivityResult;
import com.vietnam.pji.repository.LabResultRepository;
import com.vietnam.pji.repository.MedicalHistoryRepository;
import com.vietnam.pji.repository.SensitivityResultRepository;
import com.vietnam.pji.repository.SurgeryRepository;
import com.vietnam.pji.repository.medical.ClinicalRecordRepository;
import com.vietnam.pji.repository.medical.CultureResultRepository;
import com.vietnam.pji.services.episode.EpisodeService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.services.medical.ClinicalRecordService;
import com.vietnam.pji.services.medical.CultureResultService;
import com.vietnam.pji.services.medical.LabResultService;
import com.vietnam.pji.services.medical.MedicalHistoryService;
import com.vietnam.pji.services.medical.PendingLabTaskService;
import com.vietnam.pji.services.medical.SensitivityResultService;
import com.vietnam.pji.services.medical.SurgeryService;
import com.vietnam.pji.services.ocr.ImageResultService;

class EpisodeAggregateServiceImplTest {

    private final EpisodeService episodeService = mock(EpisodeService.class);
    private final MedicalHistoryService medicalHistoryService = mock(MedicalHistoryService.class);
    private final ClinicalRecordService clinicalRecordService = mock(ClinicalRecordService.class);
    private final SurgeryService surgeryService = mock(SurgeryService.class);
    private final LabResultService labResultService = mock(LabResultService.class);
    private final CultureResultService cultureResultService = mock(CultureResultService.class);
    private final SensitivityResultService sensitivityResultService = mock(SensitivityResultService.class);
    private final ImageResultService imageResultService = mock(ImageResultService.class);
    private final MedicalHistoryRepository medicalHistoryRepository = mock(MedicalHistoryRepository.class);
    private final ClinicalRecordRepository clinicalRecordRepository = mock(ClinicalRecordRepository.class);
    private final SurgeryRepository surgeryRepository = mock(SurgeryRepository.class);
    private final LabResultRepository labResultRepository = mock(LabResultRepository.class);
    private final CultureResultRepository cultureResultRepository = mock(CultureResultRepository.class);
    private final SensitivityResultRepository sensitivityResultRepository = mock(SensitivityResultRepository.class);
    private final RedisService redisService = mock(RedisService.class);
    private final PendingLabTaskService pendingLabTaskService = mock(PendingLabTaskService.class);

    private final EpisodeAggregateServiceImpl service = new EpisodeAggregateServiceImpl(
            episodeService,
            medicalHistoryService,
            clinicalRecordService,
            surgeryService,
            labResultService,
            cultureResultService,
            sensitivityResultService,
            imageResultService,
            medicalHistoryRepository,
            clinicalRecordRepository,
            surgeryRepository,
            labResultRepository,
            cultureResultRepository,
            sensitivityResultRepository,
            redisService,
            pendingLabTaskService);

    @Test
    void getFullLoadsSensitivitiesOnceAndKeepsCulturesWithoutSensitivities() {
        CultureResult firstCulture = CultureResult.builder().build();
        firstCulture.setId(10L);
        CultureResult secondCulture = CultureResult.builder().build();
        secondCulture.setId(20L);
        SensitivityResult sensitivity = SensitivityResult.builder().culture(firstCulture).build();
        sensitivity.setId(30L);
        PaginationResultDTO emptyImages = new PaginationResultDTO();
        emptyImages.setResult(List.of());

        when(episodeService.getById(1L)).thenReturn(PjiEpisode.builder().build());
        when(cultureResultRepository.findByEpisodeIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(firstCulture, secondCulture));
        when(sensitivityResultRepository.findByCultureIdIn(List.of(10L, 20L)))
                .thenReturn(List.of(sensitivity));
        when(medicalHistoryRepository.findByEpisodeId(1L)).thenReturn(Optional.empty());
        when(clinicalRecordRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        when(surgeryRepository.findByEpisodeIdOrderBySurgeryDateAsc(1L)).thenReturn(List.of());
        when(labResultRepository.findTop5ByEpisodeIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(imageResultService.getByEpisode(anyLong(), any(Pageable.class))).thenReturn(emptyImages);

        var result = service.getFull(1L);

        assertThat(result.getSensitivityMap())
                .containsEntry(10L, List.of(sensitivity))
                .containsEntry(20L, List.of());
        verify(sensitivityResultRepository).findByCultureIdIn(List.of(10L, 20L));
        verify(sensitivityResultRepository, never()).findByCultureId(anyLong());
    }
}
