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
import com.vietnam.pji.model.medical.SpecimenCulture;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.model.medical.AntibioticSusceptibility;
import com.vietnam.pji.repository.LabReportRepository;
import com.vietnam.pji.repository.MedicalHistoryRepository;
import com.vietnam.pji.repository.AntibioticSusceptibilityRepository;
import com.vietnam.pji.repository.SurgeryRepository;
import com.vietnam.pji.repository.medical.ClinicalAssessmentRepository;
import com.vietnam.pji.repository.medical.SpecimenCultureRepository;
import com.vietnam.pji.services.episode.AdmissionService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.services.medical.ClinicalAssessmentService;
import com.vietnam.pji.services.medical.SpecimenCultureService;
import com.vietnam.pji.services.medical.LabReportService;
import com.vietnam.pji.services.medical.MedicalHistoryService;
import com.vietnam.pji.services.medical.PendingLabTaskService;
import com.vietnam.pji.services.medical.AntibioticSusceptibilityService;
import com.vietnam.pji.services.medical.SurgeryService;
import com.vietnam.pji.services.ocr.ImagingStudyService;

class AdmissionAggregateServiceImplTest {

    private final AdmissionService episodeService = mock(AdmissionService.class);
    private final MedicalHistoryService medicalHistoryService = mock(MedicalHistoryService.class);
    private final ClinicalAssessmentService clinicalRecordService = mock(ClinicalAssessmentService.class);
    private final SurgeryService surgeryService = mock(SurgeryService.class);
    private final LabReportService labResultService = mock(LabReportService.class);
    private final SpecimenCultureService cultureResultService = mock(SpecimenCultureService.class);
    private final AntibioticSusceptibilityService sensitivityResultService = mock(AntibioticSusceptibilityService.class);
    private final ImagingStudyService imageResultService = mock(ImagingStudyService.class);
    private final MedicalHistoryRepository medicalHistoryRepository = mock(MedicalHistoryRepository.class);
    private final ClinicalAssessmentRepository clinicalRecordRepository = mock(ClinicalAssessmentRepository.class);
    private final SurgeryRepository surgeryRepository = mock(SurgeryRepository.class);
    private final LabReportRepository labResultRepository = mock(LabReportRepository.class);
    private final SpecimenCultureRepository cultureResultRepository = mock(SpecimenCultureRepository.class);
    private final AntibioticSusceptibilityRepository sensitivityResultRepository = mock(AntibioticSusceptibilityRepository.class);
    private final RedisService redisService = mock(RedisService.class);
    private final PendingLabTaskService pendingLabTaskService = mock(PendingLabTaskService.class);

    private final AdmissionAggregateServiceImpl service = new AdmissionAggregateServiceImpl(
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
        SpecimenCulture firstCulture = SpecimenCulture.builder().build();
        firstCulture.setId(10L);
        SpecimenCulture secondCulture = SpecimenCulture.builder().build();
        secondCulture.setId(20L);
        AntibioticSusceptibility sensitivity = AntibioticSusceptibility.builder().culture(firstCulture).build();
        sensitivity.setId(30L);
        PaginationResultDTO emptyImages = new PaginationResultDTO();
        emptyImages.setResult(List.of());

        when(episodeService.getById(1L)).thenReturn(Admission.builder().build());
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
