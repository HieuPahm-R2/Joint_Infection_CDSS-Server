package com.vietnam.pji.services.episode.impl;

import com.vietnam.pji.dto.request.EpisodeFullRequestDTO;
import com.vietnam.pji.dto.request.EpisodeFullRequestDTO.CultureItem;
import com.vietnam.pji.dto.request.EpisodeFullRequestDTO.ImageItem;
import com.vietnam.pji.dto.request.EpisodeFullRequestDTO.SensitivityItem;
import com.vietnam.pji.dto.request.EpisodeFullRequestDTO.SurgeryItem;
import com.vietnam.pji.dto.response.EpisodeFullResponseDTO;
import com.vietnam.pji.dto.response.ImageResultResponseDTO;
import com.vietnam.pji.model.medical.ClinicalRecord;
import com.vietnam.pji.model.medical.CultureResult;
import com.vietnam.pji.model.medical.LabResult;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.model.medical.SensitivityResult;
import com.vietnam.pji.model.medical.Surgery;
import com.vietnam.pji.repository.LabResultRepository;
import com.vietnam.pji.repository.MedicalHistoryRepository;
import com.vietnam.pji.repository.SensitivityResultRepository;
import com.vietnam.pji.repository.SurgeryRepository;
import com.vietnam.pji.repository.medical.ClinicalRecordRepository;
import com.vietnam.pji.repository.medical.CultureResultRepository;
import com.vietnam.pji.services.episode.EpisodeAggregateService;
import com.vietnam.pji.services.episode.EpisodeService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.services.medical.ClinicalRecordService;
import com.vietnam.pji.services.medical.CultureResultService;
import com.vietnam.pji.services.medical.LabResultService;
import com.vietnam.pji.services.medical.MedicalHistoryService;
import com.vietnam.pji.services.medical.SensitivityResultService;
import com.vietnam.pji.services.medical.SurgeryService;
import com.vietnam.pji.services.ocr.ImageResultService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
public class EpisodeAggregateServiceImpl implements EpisodeAggregateService {

    // Reuse per-entity services for create/update/delete so existing validation,
    // FK wiring and audit fields all apply; the diff (which ids to touch) lives here.
    private final EpisodeService episodeService;
    private final MedicalHistoryService medicalHistoryService;
    private final ClinicalRecordService clinicalRecordService;
    private final SurgeryService surgeryService;
    private final LabResultService labResultService;
    private final CultureResultService cultureResultService;
    private final SensitivityResultService sensitivityResultService;
    private final ImageResultService imageResultService;

    // Repositories used only for the read side + diffing existing rows.
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final ClinicalRecordRepository clinicalRecordRepository;
    private final SurgeryRepository surgeryRepository;
    private final LabResultRepository labResultRepository;
    private final CultureResultRepository cultureResultRepository;
    private final SensitivityResultRepository sensitivityResultRepository;

    private final RedisService redisService;
    private final com.vietnam.pji.services.medical.PendingLabTaskService pendingLabTaskService;

    /** Generous cap — an episode never has anywhere near this many children. */
    private static final Pageable ALL = PageRequest.of(0, 1000);

    @Override
    @Transactional(readOnly = true)
    public EpisodeFullResponseDTO getFull(Long episodeId) {
        PjiEpisode episode = episodeService.getById(episodeId); // throws if missing; inits patient

        List<CultureResult> cultures = cultureResultRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId);
        Map<Long, List<SensitivityResult>> sensitivityMap = new LinkedHashMap<>();
        for (CultureResult c : cultures) {
            sensitivityMap.put(c.getId(), sensitivityResultRepository.findByCultureId(c.getId()));
        }

        return EpisodeFullResponseDTO.builder()
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
    public EpisodeFullResponseDTO saveFull(Long episodeId, EpisodeFullRequestDTO dto) {
        Long epId = persistFull(episodeId, dto);
        return getFull(epId);
    }

    @Override
    @Transactional
    public void updateFull(Long episodeId, EpisodeFullRequestDTO dto) {
        persistFull(episodeId, dto);
    }

    private Long persistFull(Long episodeId, EpisodeFullRequestDTO dto) {
        // 1. Episode (create or update)
        PjiEpisode episode = (episodeId == null)
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
            Optional<ClinicalRecord> latest = clinicalRecordRepository
                    .findFirstByEpisodeIdOrderByCreatedAtDesc(epId);
            if (latest.isPresent()) {
                clinicalRecordService.update(latest.get().getId(), dto.getClinicalRecord());
            } else {
                clinicalRecordService.create(dto.getClinicalRecord());
            }
        }

        // 4. Lab result (latest upsert)
        if (dto.getLabResult() != null) {
            dto.getLabResult().setEpisodeId(epId);
            Optional<LabResult> latest = labResultRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(epId);
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
        List<ImageResultResponseDTO> existing = fetchImages(epId);
        Set<Long> existingIds = ids(existing, ImageResultResponseDTO::getId);
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
        List<CultureResult> existing = cultureResultRepository.findByEpisodeIdOrderByCreatedAtDesc(epId);
        Set<Long> existingIds = ids(existing, CultureResult::getId);
        Set<Long> incomingIds = ids(incoming, CultureItem::getId);

        // Delete removed cultures — clear their sensitivities first to respect the FK.
        for (CultureResult c : existing) {
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
        List<SensitivityResult> existing = sensitivityResultRepository.findByCultureId(cultureId);
        Set<Long> existingIds = ids(existing, SensitivityResult::getId);
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

    private void deleteSensitivities(List<SensitivityResult> sensitivities) {
        sensitivities.forEach(s -> sensitivityResultService.delete(s.getId()));
    }

    // ---- small utilities ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<ImageResultResponseDTO> fetchImages(Long episodeId) {
        Object result = imageResultService.getByEpisode(episodeId, ALL).getResult();
        return result == null ? new ArrayList<>() : (List<ImageResultResponseDTO>) result;
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
