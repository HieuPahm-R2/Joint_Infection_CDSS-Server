package com.vietnam.pji.services.episode.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.*;
import com.vietnam.pji.repository.*;
import com.vietnam.pji.repository.medical.ClinicalRecordRepository;
import com.vietnam.pji.repository.medical.CultureResultRepository;
import com.vietnam.pji.services.episode.EpisodeSnapshotAssemblerService;
import com.vietnam.pji.services.feat.RedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EpisodeSnapshotAssemblerServiceImpl implements EpisodeSnapshotAssemblerService {

    private final EpisodeRepository episodeRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final ClinicalRecordRepository clinicalRecordRepository;
    private final SurgeryRepository surgeryRepository;
    private final LabResultRepository labResultRepository;
    private final ImageResultRepository imageResultRepository;
    private final CultureResultRepository cultureResultRepository;
    private final SensitivityResultRepository sensitivityResultRepository;
    private final ObjectMapper objectMapper;
    private final RedisService redisService;

    private static final long SNAPSHOT_CACHE_TTL = 1800; // 30 minutes

    @Override
    public SnapshotBuildResult buildSnapshot(Long episodeId) {
        // Check cache first
        try {
            String cached = redisService.getCachedSnapshot(episodeId);
            if (cached != null) {
                log.debug("Snapshot cache hit for episodeId={}", episodeId);
                return objectMapper.readValue(cached, SnapshotBuildResult.class);
            }
        } catch (Exception e) {
            log.warn("Failed to read snapshot cache for episodeId={}, building from DB", episodeId);
        }

        SnapshotBuildResult result = buildSnapshotFromDb(episodeId);

        // Cache the result
        try {
            redisService.cacheSnapshot(episodeId, objectMapper.writeValueAsString(result), SNAPSHOT_CACHE_TTL);
            log.debug("Snapshot cached for episodeId={}", episodeId);
        } catch (Exception e) {
            log.warn("Failed to cache snapshot for episodeId={}", episodeId);
        }

        return result;
    }

    private SnapshotBuildResult buildSnapshotFromDb(Long episodeId) {
        PjiEpisode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found: " + episodeId));

        Patient patient = episode.getPatient();
        if (patient == null) {
            throw new ResourceNotFoundException("Patient not found for episode: " + episodeId);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        int totalSections = 7;
        int completedSections = 0;

        // 1. snapshot_metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("snapshot_at", Instant.now().toString());
        metadata.put("episode_id", episode.getId());
        metadata.put("patient_id", patient.getId());
        List<String> dataSources = new ArrayList<>();

        // 2. patient_demographics
        Map<String, Object> demographics = new LinkedHashMap<>();
        demographics.put("patient_code", patient.getPatientCode());
        demographics.put("full_name", patient.getFullName());
        demographics.put("date_of_birth",
                patient.getDateOfBirth() != null ? patient.getDateOfBirth().toString() : null);
        demographics.put("gender", patient.getGender() != null ? patient.getGender().name() : null);
        snapshot.put("patient_demographics", demographics);
        completedSections++;

        // 3. medical_history
        Optional<MedicalHistory> medHistOpt = medicalHistoryRepository.findByEpisodeId(episodeId);
        if (medHistOpt.isPresent()) {
            MedicalHistory mh = medHistOpt.get();
            Map<String, Object> medHistory = new LinkedHashMap<>();
            medHistory.put("medical_history", mh.getMedicalHistory());
            medHistory.put("process", mh.getProcess());

            Map<String, Object> allergies = new LinkedHashMap<>();
            allergies.put("is_allergy", mh.getIsAllergy());
            allergies.put("allergy_note", mh.getAllergyNote());
            medHistory.put("allergies", allergies);

            Map<String, Object> substance = new LinkedHashMap<>();
            substance.put("is_drug", mh.getIsDrug());
            substance.put("drug_note", mh.getDrugNote());
            substance.put("is_alcohol", mh.getIsAlcohol());
            substance.put("alcohol_note", mh.getAlcoholNote());
            substance.put("is_smoking", mh.getIsSmoking());
            substance.put("smoking_note", mh.getSmokingNote());
            substance.put("is_other", mh.getIsOther());
            substance.put("other_note", mh.getOtherNote());
            medHistory.put("substance_use", substance);

            snapshot.put("medical_history", medHistory);
            dataSources.add("medical_histories");
            completedSections++;
        }

        // 4. clinical_records (latest)
        Optional<ClinicalRecord> crOpt = clinicalRecordRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId);
        if (crOpt.isPresent()) {
            ClinicalRecord cr = crOpt.get();
            Map<String, Object> clinical = new LinkedHashMap<>();
            clinical.put("record_id", cr.getId());
            clinical.put("recorded_at", cr.getCreatedAt() != null ? cr.getCreatedAt().toInstant().toString() : null);
            clinical.put("onset_timing", cr.getOnsetTiming());
            clinical.put("illness_onset_date", null);

            Map<String, Object> vitals = new LinkedHashMap<>();
            vitals.put("blood_pressure", cr.getBloodPressure());
            vitals.put("height_cm", cr.getHeightCm());
            vitals.put("weight_kg", cr.getWeightKg());
            vitals.put("bmi", cr.getBmi());
            clinical.put("vitals", vitals);

            Map<String, Object> symptoms = new LinkedHashMap<>();
            symptoms.put("fever", cr.getFever());
            symptoms.put("pain", cr.getPain());
            symptoms.put("erythema", cr.getErythema());
            symptoms.put("swelling", cr.getSwelling());
            symptoms.put("sinus_tract", cr.getSinusTract());
            clinical.put("symptoms", symptoms);

            Map<String, Object> infection = new LinkedHashMap<>();
            infection.put("onset_timing", cr.getOnsetTiming());
            infection.put("suspected_infection_type", cr.getOnsetTiming());
            infection.put("suspected_transmission_route", cr.getSuspectedTransmissionRoute());
            infection.put("hematogenous_suspected", cr.getHematogenousSuspected());
            infection.put("implant_stability", cr.getImplantStability());
            infection.put("soft_tissue", cr.getSoftTissue());
            infection.put("pmma_allergy", cr.getPmmaAllergy());
            infection.put("prosthesis_joint", cr.getProsthesisJoint());
            infection.put("days_since_index_arthroplasty", cr.getDaysSinceIndexArthroplasty());
            clinical.put("infection_assessment", infection);

            clinical.put("notations", cr.getNotations());
            snapshot.put("clinical_records", clinical);
            dataSources.add("clinical_records");
            completedSections++;
        }

        // 5. surgeries
        List<Surgery> surgeries = surgeryRepository.findByEpisodeIdOrderBySurgeryDateAsc(episodeId);
        if (!surgeries.isEmpty()) {
            List<Map<String, Object>> surgeryItems = new ArrayList<>();
            for (Surgery s : surgeries) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("surgery_id", s.getId());
                item.put("surgery_date", s.getSurgeryDate() != null ? s.getSurgeryDate().toString() : null);
                item.put("surgery_type", s.getSurgeryType());
                item.put("findings", s.getFindings());
                surgeryItems.add(item);
            }
            Map<String, Object> surgeriesMap = new LinkedHashMap<>();
            surgeriesMap.put("items", surgeryItems);
            snapshot.put("surgeries", surgeriesMap);
            dataSources.add("surgeries");
            completedSections++;
        }

        // 6. lab_results
        Optional<LabResult> latestLabOpt = labResultRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId);
        if (latestLabOpt.isPresent()) {
            LabResult latest = latestLabOpt.get();
            Map<String, Object> labResults = new LinkedHashMap<>();
            labResults.put("latest", buildLabResultMap(latest));

            // historical trends
            List<LabResult> trends = labResultRepository.findTop5ByEpisodeIdOrderByCreatedAtDesc(episodeId);
            if (trends.size() > 1) {
                List<Map<String, Object>> trendList = new ArrayList<>();
                // Skip the first (latest) one since it's already in "latest"
                for (int i = 1; i < trends.size(); i++) {
                    trendList.add(buildLabResultMap(trends.get(i)));
                }
                labResults.put("historical_trends", trendList);
            }

            snapshot.put("lab_results", labResults);
            dataSources.add("lab_results");
            completedSections++;
        }

        // 7. image_results
        List<ImageResult> images = imageResultRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId);
        if (!images.isEmpty()) {
            List<Map<String, Object>> imageItems = new ArrayList<>();
            for (ImageResult img : images) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("image_id", img.getId());
                item.put("type", img.getType());
                item.put("created_at", img.getCreatedAt() != null ? img.getCreatedAt().toInstant().toString() : null);
                item.put("findings", img.getFindings());
                if (img.getFileMetadata() != null) {
                    try {
                        item.put("file_metadata",
                                objectMapper.readValue(img.getFileMetadata(), new TypeReference<Map<String, Object>>() {
                                }));
                    } catch (Exception e) {
                        item.put("file_metadata", img.getFileMetadata());
                    }
                }
                imageItems.add(item);
            }
            Map<String, Object> imagesMap = new LinkedHashMap<>();
            imagesMap.put("items", imageItems);
            snapshot.put("image_results", imagesMap);
            dataSources.add("image_results");
        }

        // 8. culture_results with nested sensitivities
        List<CultureResult> cultures = cultureResultRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId);
        if (!cultures.isEmpty()) {
            List<Map<String, Object>> cultureItems = new ArrayList<>();
            for (CultureResult c : cultures) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("culture_id", c.getId());
                item.put("organism_name", c.getName());
                item.put("result_status", c.getResult());
                item.put("gram_type", c.getGramType());
                item.put("sample_type", c.getSampleType());
                item.put("had_antibiotics_before", c.getAntibioticed());
                item.put("days_off_antibiotics", c.getDaysOffAntibio());
                item.put("incubation_days", c.getIncubationDays());
                item.put("notes", c.getNotes());

                // Nest sensitivities
                List<SensitivityResult> sensitivities = sensitivityResultRepository.findByCultureId(c.getId());
                if (!sensitivities.isEmpty()) {
                    List<Map<String, Object>> sensList = new ArrayList<>();
                    for (SensitivityResult sr : sensitivities) {
                        Map<String, Object> sensItem = new LinkedHashMap<>();
                        sensItem.put("antibiotic_name", sr.getAntibioticName());
                        sensItem.put("mic_value", sr.getMicValue());
                        sensItem.put("sensitivity_code", sr.getSensitivityCode());
                        sensList.add(sensItem);
                    }
                    item.put("sensitivities", sensList);
                }
                cultureItems.add(item);
            }
            Map<String, Object> culturesMap = new LinkedHashMap<>();
            culturesMap.put("items", cultureItems);
            snapshot.put("culture_results", culturesMap);
            dataSources.add("culture_results");
            dataSources.add("sensitivity_results");
            completedSections++;
        }

        // Finalize metadata
        metadata.put("data_sources", dataSources);
        snapshot.put("snapshot_metadata", metadata);

        // Reorder: metadata first
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("snapshot_metadata", snapshot.get("snapshot_metadata"));
        ordered.put("patient_demographics", snapshot.get("patient_demographics"));
        if (snapshot.containsKey("medical_history"))
            ordered.put("medical_history", snapshot.get("medical_history"));
        if (snapshot.containsKey("clinical_records"))
            ordered.put("clinical_records", snapshot.get("clinical_records"));
        if (snapshot.containsKey("surgeries"))
            ordered.put("surgeries", snapshot.get("surgeries"));
        if (snapshot.containsKey("lab_results"))
            ordered.put("lab_results", snapshot.get("lab_results"));
        if (snapshot.containsKey("image_results"))
            ordered.put("image_results", snapshot.get("image_results"));
        if (snapshot.containsKey("culture_results"))
            ordered.put("culture_results", snapshot.get("culture_results"));

        BigDecimal completeness = totalSections > 0
                ? BigDecimal.valueOf(completedSections * 100.0 / totalSections).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;

        return SnapshotBuildResult.builder()
                .snapshotDataJson(ordered)
                .completenessScore(completeness)
                .build();
    }

    private Map<String, Object> buildLabResultMap(LabResult lr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lab_id", lr.getId());
        map.put("created_at", lr.getCreatedAt() != null ? lr.getCreatedAt().toInstant().toString() : null);

        if (lr.getHematologyTests() != null) {
            map.put("hematology_tests", lr.getHematologyTests());
        }

        if (lr.getFluidAnalysis() != null) {
            map.put("fluid_analysis", lr.getFluidAnalysis());
        }

        if (lr.getBiochemicalData() != null) {
            map.put("biochemical_data", lr.getBiochemicalData());
        }

        return map;
    }
}
