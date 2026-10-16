package com.vietnam.pji.services.episode.impl;

import com.vietnam.pji.dto.request.AdmissionFullRequestDTO;
import com.vietnam.pji.dto.request.ClinicalAssessmentRequestDTO;
import com.vietnam.pji.dto.request.AdmissionFullRequestDTO.CultureItem;
import com.vietnam.pji.dto.request.AdmissionFullRequestDTO.ImageItem;
import com.vietnam.pji.dto.request.AdmissionFullRequestDTO.SensitivityItem;
import com.vietnam.pji.dto.request.AdmissionFullRequestDTO.SurgeryItem;
import com.vietnam.pji.dto.response.AdmissionFullResponseDTO;
import com.vietnam.pji.dto.response.ImagingStudyResponseDTO;
import com.vietnam.pji.model.medical.ClinicalAssessment;
import com.vietnam.pji.model.medical.VitalSignsMeasurement;
import com.vietnam.pji.model.medical.SpecimenCulture;
import com.vietnam.pji.model.medical.LabReport;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.model.medical.AntibioticSusceptibility;
import com.vietnam.pji.model.medical.Surgery;
import com.vietnam.pji.repository.LabReportRepository;
import com.vietnam.pji.repository.MedicalHistoryRepository;
import com.vietnam.pji.repository.AntibioticSusceptibilityRepository;
import com.vietnam.pji.repository.SurgeryRepository;
import com.vietnam.pji.repository.medical.ClinicalAssessmentRepository;
import com.vietnam.pji.repository.medical.VitalSignsMeasurementRepository;
import com.vietnam.pji.repository.medical.SpecimenCultureRepository;
import com.vietnam.pji.services.episode.AdmissionAggregateService;
import com.vietnam.pji.services.episode.AdmissionService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.services.medical.ClinicalAssessmentService;
import com.vietnam.pji.services.medical.SpecimenCultureService;
import com.vietnam.pji.services.medical.LabReportService;
import com.vietnam.pji.services.medical.MedicalHistoryService;
import com.vietnam.pji.services.medical.AntibioticSusceptibilityService;
import com.vietnam.pji.services.medical.SurgeryService;
import com.vietnam.pji.services.ocr.ImagingStudyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdmissionAggregateServiceImpl implements AdmissionAggregateService {

    // Reuse per-entity services for create/update/delete so existing validation,
    // FK wiring and audit fields all apply; the diff (which ids to touch) lives here.
    private final AdmissionService episodeService;
    private final MedicalHistoryService medicalHistoryService;
    private final ClinicalAssessmentService clinicalRecordService;
    private final SurgeryService surgeryService;
    private final LabReportService labResultService;
    private final SpecimenCultureService cultureResultService;
    private final AntibioticSusceptibilityService sensitivityResultService;
    private final ImagingStudyService imageResultService;

    // Repositories used only for the read side + diffing existing rows.
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final ClinicalAssessmentRepository clinicalRecordRepository;
    private final SurgeryRepository surgeryRepository;
    private final LabReportRepository labResultRepository;
    private final SpecimenCultureRepository cultureResultRepository;
    private final AntibioticSusceptibilityRepository sensitivityResultRepository;
    private final VitalSignsMeasurementRepository vitalSignsRepository;

    private final RedisService redisService;
    private final com.vietnam.pji.services.medical.PendingLabTaskService pendingLabTaskService;

    /** Generous cap — an episode never has anywhere near this many children. */
    private static final Pageable ALL = PageRequest.of(0, 1000);

    @Override
    @Transactional(readOnly = true)
    public AdmissionFullResponseDTO getFull(Long episodeId) {
        Admission episode = episodeService.getById(episodeId); // throws if missing; inits patient

        List<SpecimenCulture> cultures = cultureResultRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId);
        Map<Long, List<AntibioticSusceptibility>> sensitivityMap = cultures.stream()
                .collect(Collectors.toMap(
                        SpecimenCulture::getId,
                        ignored -> new ArrayList<>(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (!cultures.isEmpty()) {
            sensitivityResultRepository.findByCultureIdIn(cultures.stream().map(SpecimenCulture::getId).toList())
                    .forEach(sensitivity -> sensitivityMap
                            .get(sensitivity.getCulture().getId())
                            .add(sensitivity));
        }

        return AdmissionFullResponseDTO.builder()
                .episode(episode)
                .medicalHistory(medicalHistoryRepository.findByEpisodeId(episodeId).orElse(null))
                .clinicalRecord(clinicalRecordRepository
                        .findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId).orElse(null))
                .surgeries(surgeryRepository.findByEpisodeIdOrderBySurgeryDateAsc(episodeId))
                .labResults(labResultRepository.findTop5ByEpisodeIdOrderByCreatedAtDesc(episodeId))
                .imageResults(fetchImages(episodeId))
                .cultureResults(cultures)
                .sensitivityMap(sensitivityMap)
                .build();
    }

    @Override
    @Transactional
    public AdmissionFullResponseDTO saveFull(Long episodeId, AdmissionFullRequestDTO dto) {
        Long epId = persistFull(episodeId, dto);
        return getFull(epId);
    }

    @Override
    @Transactional
    public void updateFull(Long episodeId, AdmissionFullRequestDTO dto) {
        persistFull(episodeId, dto);
    }

    private Long persistFull(Long episodeId, AdmissionFullRequestDTO dto) {
        // 1. Episode (create or update)
        Admission episode = (episodeId == null)
                ? episodeService.create(dto.getEpisode())
                : episodeService.update(episodeId, dto.getEpisode());
        Long epId = episode.getId();

        // 2. Medical history (1-1 upsert)
        if (dto.getMedicalHistory() != null) {
            if (medicalHistoryRepository.existsByEpisodeId(epId)) {
                medicalHistoryService.update(epId, dto.getMedicalHistory());
            } else {
                medicalHistoryService.create(epId, dto.getMedicalHistory());
            }
        }

        // 3. Clinical record (latest upsert)
        if (dto.getClinicalRecord() != null) {
            dto.getClinicalRecord().setEpisodeId(epId);
            Optional<ClinicalAssessment> latest = clinicalRecordRepository
                    .findFirstByEpisodeIdOrderByCreatedAtDesc(epId);
            if (latest.isPresent()) {
                clinicalRecordService.update(latest.get().getId(), dto.getClinicalRecord());
            } else {
                clinicalRecordService.create(dto.getClinicalRecord());
            }
        }

        // 3b. Vital signs (append-only history)
        if (dto.getClinicalRecord() != null) {
            recordVitalSigns(episode, dto.getClinicalRecord());
        }

        // 4. Lab result (latest upsert)
        if (dto.getLabResult() != null) {
            dto.getLabResult().setEpisodeId(epId);
            Optional<LabReport> latest = labResultRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(epId);
            if (latest.isPresent()) {
                labResultService.update(latest.get().getId(), dto.getLabResult());
            } else {
                labResultService.create(dto.getLabResult());
            }
        }

        // 5. Surgeries (diff)
        syncSurgeries(epId, nullSafe(dto.getSurgeries()));

        // 6. Images (diff)
        syncImages(epId, nullSafe(dto.getImages()));

        // 7. Cultures + nested sensitivities (diff)
        syncCultures(epId, nullSafe(dto.getCultures()));

        // Lab tasks auto-fulfil inside labResultService.create/update (step 4).
        // Resolve the clinical/culture pending tasks here now that the clinical
        // record, allergies, cultures and surgeries have all been persisted.
        pendingLabTaskService.autoFulfillClinicalForEpisode(epId);

        // Bust the AI snapshot cache so the next recommendation sees fresh data.
        redisService.evictSnapshotCache(epId);

        return epId;
    }

    /**
     * Appends a vital-signs measurement when the numbers actually changed.
     *
     * <p>
     * Decision 0010 moves the four vital fields off the assessment and onto a 1:N
     * history, because overwriting them loses the clinical picture at every earlier
     * moment. The assessment keeps the fields during the transition, so this write
     * is additive and nothing reads the history yet.
     *
     * <p>
     * A save that does not change the numbers must not append a row, otherwise the
     * history fills with duplicates of the same measurement.
     */
    private void recordVitalSigns(Admission episode, ClinicalAssessmentRequestDTO request) {
        boolean hasAnyVital = request.getBloodPressure() != null
                || request.getHeightCm() != null
                || request.getWeightKg() != null
                || request.getBmi() != null;
        if (!hasAnyVital) {
            return;
        }

        Optional<VitalSignsMeasurement> latest = vitalSignsRepository
                .findFirstByEpisodeIdOrderByRecordedAtDesc(episode.getId());
        if (latest.isPresent() && sameVitals(latest.get(), request)) {
            return;
        }

        vitalSignsRepository.save(VitalSignsMeasurement.builder()
                .episode(episode)
                .recordedAt(Instant.now())
                .bloodPressure(request.getBloodPressure())
                .heightCm(request.getHeightCm())
                .weightKg(request.getWeightKg())
                .bmi(request.getBmi())
                .build());
    }

    private static boolean sameVitals(VitalSignsMeasurement latest, ClinicalAssessmentRequestDTO request) {
        return Objects.equals(latest.getBloodPressure(), request.getBloodPressure())
                && sameNumber(latest.getHeightCm(), request.getHeightCm())
                && sameNumber(latest.getWeightKg(), request.getWeightKg())
                && sameNumber(latest.getBmi(), request.getBmi());
    }

    /** {@code BigDecimal.equals} compares scale, so 1.7 and 1.70 would look different. */
    private static boolean sameNumber(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    // ---- child sync helpers -------------------------------------------------

    private void syncSurgeries(Long epId, List<SurgeryItem> incoming) {
        List<Surgery> existing = surgeryRepository.findByEpisodeIdOrderBySurgeryDateAsc(epId);
        Set<Long> existingIds = ids(existing, Surgery::getId);
        Set<Long> incomingIds = ids(incoming, SurgeryItem::getId);

        existing.stream()
                .filter(s -> !incomingIds.contains(s.getId()))
                .forEach(s -> surgeryService.delete(s.getId()));

        for (SurgeryItem item : incoming) {
            // Mirror the frontend filter — skip incomplete surgery rows.
            if (!StringUtils.hasText(item.getSurgeryType()) || item.getSurgeryDate() == null) {
                continue;
            }
            item.setEpisodeId(epId);
            if (isUpdate(item.getId(), existingIds)) {
                surgeryService.update(item.getId(), item);
            } else {
                surgeryService.create(item);
            }
        }
    }

    private void syncImages(Long epId, List<ImageItem> incoming) {
        List<ImagingStudyResponseDTO> existing = fetchImages(epId);
        Set<Long> existingIds = ids(existing, ImagingStudyResponseDTO::getId);
        Set<Long> incomingIds = ids(incoming, ImageItem::getId);

        existing.stream()
                .filter(img -> !incomingIds.contains(img.getId()))
                .forEach(img -> imageResultService.delete(img.getId()));

        for (ImageItem item : incoming) {
            item.setEpisodeId(epId);
            if (isUpdate(item.getId(), existingIds)) {
                imageResultService.update(item.getId(), item);
            } else {
                imageResultService.create(item);
            }
        }
    }

    private void syncCultures(Long epId, List<CultureItem> incoming) {
        List<SpecimenCulture> existing = cultureResultRepository.findByEpisodeIdOrderByCreatedAtDesc(epId);
        Set<Long> existingIds = ids(existing, SpecimenCulture::getId);
        Set<Long> incomingIds = ids(incoming, CultureItem::getId);

        // Delete removed cultures — clear their sensitivities first to respect the FK.
        for (SpecimenCulture c : existing) {
            if (!incomingIds.contains(c.getId())) {
                deleteSensitivities(sensitivityResultRepository.findByCultureId(c.getId()));
                cultureResultService.delete(c.getId());
            }
        }

        for (CultureItem item : incoming) {
            item.setEpisodeId(epId);
            Long cultureId;
            if (isUpdate(item.getId(), existingIds)) {
                cultureResultService.update(item.getId(), item);
                cultureId = item.getId();
            } else {
                cultureId = cultureResultService.create(item).getId();
            }
            syncSensitivities(cultureId, nullSafe(item.getSensitivities()));
        }
    }

    private void syncSensitivities(Long cultureId, List<SensitivityItem> incoming) {
        List<AntibioticSusceptibility> existing = sensitivityResultRepository.findByCultureId(cultureId);
        Set<Long> existingIds = ids(existing, AntibioticSusceptibility::getId);
        Set<Long> incomingIds = ids(incoming, SensitivityItem::getId);

        existing.stream()
                .filter(s -> !incomingIds.contains(s.getId()))
                .forEach(s -> sensitivityResultService.delete(s.getId()));

        for (SensitivityItem item : incoming) {
            if (!StringUtils.hasText(item.getAntibioticName())) {
                continue;
            }
            item.setCultureId(cultureId);
            if (isUpdate(item.getId(), existingIds)) {
                sensitivityResultService.update(item.getId(), item);
            } else {
                sensitivityResultService.create(item);
            }
        }
    }

    private void deleteSensitivities(List<AntibioticSusceptibility> sensitivities) {
        sensitivities.forEach(s -> sensitivityResultService.delete(s.getId()));
    }

    // ---- small utilities ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<ImagingStudyResponseDTO> fetchImages(Long episodeId) {
        Object result = imageResultService.getByEpisode(episodeId, ALL).getResult();
        return result == null ? new ArrayList<>() : (List<ImagingStudyResponseDTO>) result;
    }

    private static boolean isUpdate(Long id, Set<Long> existingIds) {
        return id != null && existingIds.contains(id);
    }

    /** Non-null ids of a collection (works for both existing entities and incoming items). */
    private static <T> Set<Long> ids(List<T> items, java.util.function.Function<T, Long> idOf) {
        return items.stream().map(idOf).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
