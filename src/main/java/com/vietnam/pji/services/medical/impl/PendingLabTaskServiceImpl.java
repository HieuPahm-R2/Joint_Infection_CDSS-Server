package com.vietnam.pji.services.medical.impl;

import com.vietnam.pji.constant.PendingLabTaskStatus;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.PendingLabTask;
import com.vietnam.pji.model.medical.LabResult;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.LabResultRepository;
import com.vietnam.pji.repository.PendingLabTaskRepository;
import com.vietnam.pji.services.medical.PendingLabTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingLabTaskServiceImpl implements PendingLabTaskService {

    private final PendingLabTaskRepository pendingLabTaskRepository;
    private final LabResultRepository labResultRepository;
    private final EpisodeRepository episodeRepository;
    private final com.vietnam.pji.repository.ai.AiRecommendationRunRepository aiRecommendationRunRepository;
    private final com.vietnam.pji.repository.medical.ClinicalRecordRepository clinicalRecordRepository;
    private final com.vietnam.pji.repository.MedicalHistoryRepository medicalHistoryRepository;
    private final com.vietnam.pji.repository.medical.CultureResultRepository cultureResultRepository;
    private final com.vietnam.pji.repository.SurgeryRepository surgeryRepository;

    /**
     * Maps completeness field keys from Python's completeness.py to the JSONB
     * storage location and canonical frontend identifier inside LabResult.
     * Sections: "hematology" (hematologyTests), "fluid" (fluidAnalysis),
     * "biochemical" (biochemicalData).
     * The id matches the canonical frontend row when one exists (e.g. ht_17 for
     * D-dimer); otherwise it uses a {section}_extra_{slug} prefix so the row
     * still merges into the clinician view as an AI-requested row.
     */
    private record LabFieldSpec(String section, String id, String name,
            String normalRange, String defaultUnit) {
    }

    private static final Map<String, LabFieldSpec> FIELD_TO_LAB_MAPPING = Map.ofEntries(
            Map.entry("serum_CRP",
                    new LabFieldSpec("hematology", "ht_extra_crp", "CRP", "< 5", "mg/L")),
            Map.entry("serum_ESR",
                    new LabFieldSpec("hematology", "ht_7", "Máu lắng", "< 10", "mm")),
            Map.entry("serum_D_Dimer",
                    new LabFieldSpec("hematology", "ht_17", "D-dimer", "< 0.5", "mg/L FEU")),
            Map.entry("serum_IL6",
                    new LabFieldSpec("hematology", "ht_18", "Serum IL-6", "< 7.0", "pg/mL")),
            Map.entry("synovial_WBC",
                    new LabFieldSpec("fluid", "fa_3", "Bạch cầu (Dịch)", "", "Tế bào/Vi trường")),
            Map.entry("synovial_PMN",
                    new LabFieldSpec("fluid", "fa_6", "%PMN (Dịch)", "", "%")),
            Map.entry("synovial_CRP",
                    new LabFieldSpec("fluid", "fa_5", "Định lượng CRP (Dịch)", "< 6.9", "mg/l")),
            Map.entry("synovial_alpha_defensin",
                    new LabFieldSpec("fluid", "fa_extra_alpha_defensin",
                            "Alpha Defensin (dịch)", "< 0.12", "ug/mL")),
            Map.entry("synovial_LE",
                    new LabFieldSpec("fluid", "fa_extra_leukocyte_esterase",
                            "Leukocyte Esterase (dịch)", "10 - 25", "LEU/µL")),
            Map.entry("renal_function",
                    new LabFieldSpec("biochemical", "bc_6", "Creatinine", "59 - 104", "µmol/l")),
            Map.entry("liver_function",
                    new LabFieldSpec("biochemical", "bc_9", "ALT", "35 - 52", "U/L")));

    /**
     * Build a self-describing spec for a field the AI proposed but isn't in the
     * static mapping above. Defaults to the hematology section since that's the
     * most common shape for serum biomarkers; clinician can move the row if needed.
     */
    private static LabFieldSpec fallbackSpec(String field) {
        String slug = field == null ? "unknown"
                : field.toLowerCase().replaceAll("[^a-z0-9]+", "_")
                        .replaceAll("(^_+|_+$)", "");
        if (slug.isEmpty())
            slug = "unknown";
        return new LabFieldSpec("hematology", "ht_extra_" + slug, field, "", "");
    }

    /**
     * Canonical lowercase biochemical_data key for fields stored in that
     * section, so the frontend's id mapping (creatinine → bc_6, alt → bc_9)
     * resolves and the Rag_Agentic completeness check finds the value.
     */
    private static final Map<String, String> FIELD_TO_BIOCHEM_KEY = Map.of(
            "renal_function", "creatinine",
            "liver_function", "alt");

    private static final Set<String> ELIGIBLE_COMPLETENESS_FIELDS = Set.of(
            "sinus_tract",
            "culture_results",
            "positive_histology",
            "infection_type",
            "implant_stability",
            "allergies",
            "serum_CRP",
            "serum_ESR",
            "serum_D_Dimer",
            "serum_IL6",
            "synovial_WBC",
            "synovial_PMN",
            "synovial_CRP",
            "synovial_alpha_defensin",
            "synovial_LE",
            "renal_function",
            "liver_function");

    /**
     * Diacritic-insensitive name aliases used by {@link #autoFulfillForEpisode}
     * to recognise a clinician-entered row as satisfying a pending field. Kept
     * in sync with the Rag_Agentic completeness aliases and the frontend form
     * row names. Matched against normalized row id and row name.
     */
    private static final Map<String, Set<String>> FIELD_NAME_ALIASES = Map.ofEntries(
            Map.entry("serum_CRP", Set.of("htextracrp", "crp")),
            Map.entry("serum_ESR", Set.of("ht7", "maulang", "esr", "tocdomaulang")),
            Map.entry("serum_D_Dimer", Set.of("ht17", "ddimer")),
            Map.entry("serum_IL6", Set.of("ht18", "il6")),
            Map.entry("synovial_WBC", Set.of("fa3", "synovialwbc", "bachcaudich")),
            Map.entry("synovial_PMN", Set.of("fa6", "synovialpmn", "pmndich")),
            Map.entry("synovial_alpha_defensin",
                    Set.of("faextraalphadefensin", "ht19", "alphadefensin")),
            Map.entry("synovial_LE",
                    Set.of("faextraleukocyteesterase", "ht15", "leukocyteesterase")),
            Map.entry("renal_function", Set.of("bc6", "creatinin", "creatinine", "egfr")),
            Map.entry("liver_function",
                    Set.of("bc8", "bc9", "alt", "ast", "hoatdoalt", "hoatdoast")));

    /** Lowercase, strip Vietnamese diacritics, drop non-alphanumerics. */
    private static String normalizeToken(Object value) {
        if (value == null)
            return "";
        String s = java.text.Normalizer.normalize(value.toString(), java.text.Normalizer.Form.NFD)
                .toLowerCase()
                .replace("đ", "d");
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c) && c < 128) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingLabTask> getMyPendingTasks(Long userId) {
        // Return PENDING + FULFILLED (not DISMISSED) so the tooltip and the
        // in-episode tab can show per-episode progress, not just the open items.
        List<PendingLabTask> tasks = pendingLabTaskRepository
                .findByAssignedToUserIdAndStatusInOrderByCreatedAtDesc(
                        userId,
                        List.of(PendingLabTaskStatus.PENDING, PendingLabTaskStatus.FULFILLED));
        tasks.forEach(t -> {
            Hibernate.initialize(t.getEpisode());
            if (t.getEpisode() != null) {
                Hibernate.initialize(t.getEpisode().getPatient());
            }
            Hibernate.initialize(t.getPatient());
            if (t.getFulfilledLabResult() != null) {
                Hibernate.initialize(t.getFulfilledLabResult());
                Hibernate.initialize(t.getFulfilledLabResult().getEpisode());
                if (t.getFulfilledLabResult().getEpisode() != null) {
                    Hibernate.initialize(t.getFulfilledLabResult().getEpisode().getPatient());
                }
            }
        });
        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public long getMyPendingCount(Long userId) {
        // Count distinct episodes still carrying pending work — the badge tracks
        // episodes, not individual fields, so it only drops when an episode is
        // fully resolved.
        return pendingLabTaskRepository.countDistinctEpisodesByAssignedToUserIdAndStatus(
                userId, PendingLabTaskStatus.PENDING);
    }

    @Override
    @Transactional
    public void dismiss(Long taskId) {
        PendingLabTask task = pendingLabTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Pending task not found"));
        task.setStatus(PendingLabTaskStatus.DISMISSED);
        pendingLabTaskRepository.save(task);
    }

    @Override
    @Transactional
    public void fulfillByQuickEntry(Long taskId, Object value, String unit) {
        PendingLabTask task = pendingLabTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Pending task not found"));

        if (task.getStatus() != PendingLabTaskStatus.PENDING) {
            return;
        }

        Long episodeId = task.getEpisode().getId();
        String field = task.getField();
        LabFieldSpec spec = FIELD_TO_LAB_MAPPING.getOrDefault(field, fallbackSpec(field));

        // Get or create the latest lab result for this episode
        LabResult labResult = getOrCreateLatestLabResult(episodeId);

        // Self-describing row: id + name + normalRange let the frontend merge into
        // the canonical default row (when one exists) or render the AI-proposed row
        // via the ht_extra_/fa_extra_ namespace without crashing.
        Map<String, Object> testEntry = new LinkedHashMap<>();
        testEntry.put("id", spec.id());
        testEntry.put("name", spec.name());
        testEntry.put("value", value);
        testEntry.put("unit", unit != null ? unit : spec.defaultUnit());
        testEntry.put("normalRange", spec.normalRange());

        switch (spec.section()) {
            case "hematology" -> {
                List<Map<String, Object>> tests = labResult.getHematologyTests();
                if (tests == null)
                    tests = new ArrayList<>();
                removeExisting(tests, spec.id(), spec.name());
                tests.add(testEntry);
                labResult.setHematologyTests(tests);
            }
            case "fluid" -> {
                List<Map<String, Object>> tests = labResult.getFluidAnalysis();
                if (tests == null)
                    tests = new ArrayList<>();
                removeExisting(tests, spec.id(), spec.name());
                tests.add(testEntry);
                labResult.setFluidAnalysis(tests);
            }
            case "biochemical" -> {
                Map<String, Object> data = labResult.getBiochemicalData();
                if (data == null)
                    data = new LinkedHashMap<>();
                // Store under the canonical lowercase key ({value, unit} shape)
                // so the frontend id-mapping and the AI completeness check both
                // resolve it; falls back to the spec name for unmapped fields.
                String biochemKey = FIELD_TO_BIOCHEM_KEY.getOrDefault(field, spec.name());
                Map<String, Object> biochemEntry = new LinkedHashMap<>();
                biochemEntry.put("value", value);
                biochemEntry.put("unit", unit != null ? unit : spec.defaultUnit());
                data.put(biochemKey, biochemEntry);
                labResult.setBiochemicalData(data);
            }
        }

        LabResult saved = labResultRepository.save(labResult);

        // Mark task as fulfilled (drops any stale FULFILLED row for the field)
        markFulfilled(task, saved);

        log.info("Quick-entry fulfilled: taskId={}, field={}, episodeId={}", taskId, field, episodeId);
    }

    @Override
    @Transactional
    public void createFromCompleteness(Long episodeId, Long patientId, Long userId,
            Long runId, List<Map<String, Object>> missingItems) {
        if (missingItems == null || missingItems.isEmpty())
            return;

        PjiEpisode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));

        for (Map<String, Object> item : missingItems) {
            String field = (String) item.get("field");
            if (!ELIGIBLE_COMPLETENESS_FIELDS.contains(field)) {
                log.warn("Skipping unsupported completeness field: episodeId={}, field={}", episodeId, field);
                continue;
            }
            String category = (String) item.get("category");
            String importance = (String) item.get("importance");
            String message = (String) item.get("message");
            // Render metadata emitted by the Rag_Agentic completeness check.
            String inputType = (String) item.get("input_type");
            String section = (String) item.get("section");
            String unit = (String) item.get("unit");
            String normalRange = (String) item.get("normal_range");

            Optional<PendingLabTask> existing = pendingLabTaskRepository
                    .findByEpisodeIdAndFieldAndStatus(episodeId, field, PendingLabTaskStatus.PENDING);
            if (existing.isPresent()) {
                PendingLabTask task = existing.get();
                // Backfill render metadata on tasks the async worker created
                // before this richer payload was available.
                if (task.getInputType() == null && inputType != null) {
                    task.setInputType(inputType);
                    task.setSection(section);
                    task.setUnit(unit);
                    task.setNormalRange(normalRange);
                }
                if (userId != null) {
                    // Async worker can create unassigned tasks before the user clicks save.
                    // Claim those tasks so they show up in the current user's drawer.
                    if (task.getAssignedToUserId() == null) {
                        task.setAssignedToUserId(userId);
                    }
                    if (task.getCreatedFromRunId() == null) {
                        task.setCreatedFromRunId(runId);
                    }
                }
                pendingLabTaskRepository.save(task);
                continue;
            }

            PendingLabTask task = PendingLabTask.builder()
                    .episode(episode)
                    .patient(episode.getPatient())
                    .assignedToUserId(userId)
                    .field(field)
                    .category(category)
                    .importance(importance)
                    .message(message)
                    .inputType(inputType)
                    .section(section)
                    .unit(unit)
                    .normalRange(normalRange)
                    .status(PendingLabTaskStatus.PENDING)
                    .createdFromRunId(runId)
                    .build();

            pendingLabTaskRepository.save(task);
        }

        // A non-null userId means this is the doctor's explicit "Lưu nhắc nhở"
        // action (the async worker passes null) — persist the saved marker so
        // the button stays disabled across reloads/devices.
        if (userId != null && runId != null) {
            aiRecommendationRunRepository.findById(runId).ifPresent(run -> {
                if (!run.isPendingTasksSaved()) {
                    run.setPendingTasksSaved(true);
                    aiRecommendationRunRepository.save(run);
                }
            });
        }

        log.info("Created pending tasks from completeness: episodeId={}, runId={}, count={}",
                episodeId, runId, missingItems.size());
    }

    @Override
    @Transactional
    public void autoFulfillForEpisode(Long episodeId, Long labResultId,
            List<Map<String, Object>> hematologyTests,
            List<Map<String, Object>> fluidAnalysis,
            Map<String, Object> biochemicalData) {
        List<PendingLabTask> pendingTasks = pendingLabTaskRepository
                .findByEpisodeIdAndStatus(episodeId, PendingLabTaskStatus.PENDING);

        if (pendingTasks.isEmpty())
            return;

        Set<String> presentTokens = extractPresentFields(hematologyTests, fluidAnalysis, biochemicalData);

        LabResult labResult = labResultRepository.findById(labResultId).orElse(null);

        for (PendingLabTask task : pendingTasks) {
            if (isFieldPresent(task.getField(), presentTokens)) {
                markFulfilled(task, labResult);
                log.info("Auto-fulfilled pending task: id={}, field={}", task.getId(), task.getField());
            }
        }
    }

    /**
     * True when a clinician-entered datum (captured as a normalized token from
     * a row id/name or a biochemical key) satisfies the given completeness
     * field. Matches the canonical row id and the diacritic-insensitive name
     * aliases shared with the Rag_Agentic completeness check.
     */
    private boolean isFieldPresent(String field, Set<String> presentTokens) {
        if (field == null || presentTokens.isEmpty()) {
            return false;
        }
        LabFieldSpec spec = FIELD_TO_LAB_MAPPING.get(field);
        if (spec != null) {
            if (presentTokens.contains(normalizeToken(spec.id()))
                    || presentTokens.contains(normalizeToken(spec.name()))) {
                return true;
            }
        }
        Set<String> aliases = FIELD_NAME_ALIASES.get(field);
        if (aliases == null) {
            return false;
        }
        return aliases.stream().anyMatch(alias ->
                presentTokens.stream().anyMatch(token -> token.contains(alias)));
    }

    @Override
    @Transactional
    public void autoFulfillClinicalForEpisode(Long episodeId) {
        List<PendingLabTask> pendingTasks = pendingLabTaskRepository
                .findByEpisodeIdAndStatus(episodeId, PendingLabTaskStatus.PENDING);
        if (pendingTasks.isEmpty())
            return;

        // Only load what the clinical/culture checks below actually need.
        boolean needsClinical = pendingTasks.stream().anyMatch(t -> {
            String f = t.getField();
            return "sinus_tract".equals(f) || "infection_type".equals(f)
                    || "implant_stability".equals(f);
        });
        com.vietnam.pji.model.medical.ClinicalRecord cr = needsClinical
                ? clinicalRecordRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId).orElse(null)
                : null;
        com.vietnam.pji.model.medical.MedicalHistory mh = pendingTasks.stream()
                .anyMatch(t -> "allergies".equals(t.getField()))
                        ? medicalHistoryRepository.findByEpisodeId(episodeId).orElse(null)
                        : null;
        List<com.vietnam.pji.model.medical.CultureResult> cultures = pendingTasks.stream()
                .anyMatch(t -> "culture_results".equals(t.getField()))
                        ? cultureResultRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId)
                        : List.of();
        List<com.vietnam.pji.model.medical.Surgery> surgeries = pendingTasks.stream()
                .anyMatch(t -> "positive_histology".equals(t.getField()))
                        ? surgeryRepository.findByEpisodeIdOrderBySurgeryDateAsc(episodeId)
                        : List.of();

        for (PendingLabTask task : pendingTasks) {
            if (isClinicalFieldSatisfied(task.getField(), cr, mh, cultures, surgeries)) {
                markFulfilled(task, null);
                log.info("Auto-fulfilled clinical pending task: id={}, field={}",
                        task.getId(), task.getField());
            }
        }
    }

    private boolean isClinicalFieldSatisfied(String field,
            com.vietnam.pji.model.medical.ClinicalRecord cr,
            com.vietnam.pji.model.medical.MedicalHistory mh,
            List<com.vietnam.pji.model.medical.CultureResult> cultures,
            List<com.vietnam.pji.model.medical.Surgery> surgeries) {
        if (field == null)
            return false;
        return switch (field) {
            case "sinus_tract" -> cr != null && cr.getSinusTract() != null;
            case "infection_type" -> cr != null
                    && cr.getOnsetTiming() != null;
            case "implant_stability" -> cr != null
                    && cr.getImplantStability() != null
                    && cr.getImplantStability() != com.vietnam.pji.constant.ImplantType.UNKNOWN;
            case "allergies" -> mh != null && mh.getIsAllergy() != null;
            case "culture_results" -> hasUsableCultureResult(cultures);
            case "positive_histology" -> hasHistologyFindings(surgeries);
            default -> false;
        };
    }

    private boolean hasHistologyFindings(List<com.vietnam.pji.model.medical.Surgery> surgeries) {
        if (surgeries == null)
            return false;
        for (com.vietnam.pji.model.medical.Surgery s : surgeries) {
            if (s.getPositiveHistology() != null) {
                return true;
            }
            String f = s.getFindings() == null ? "" : s.getFindings().toLowerCase();
            if (f.contains("giai phau benh") || f.contains("giải phẫu bệnh")
                    || f.contains("sinh thiet") || f.contains("sinh thiết")
                    || f.contains("histolog") || f.contains("patholog")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUsableCultureResult(List<com.vietnam.pji.model.medical.CultureResult> cultures) {
        if (cultures == null) {
            return false;
        }
        for (com.vietnam.pji.model.medical.CultureResult c : cultures) {
            if (c.getResult() != null
                    && c.getResult() != com.vietnam.pji.constant.CultureStatus.PENDING
                    && c.getResult() != com.vietnam.pji.constant.CultureStatus.NOT_PERFORMED) {
                return true;
            }
            if (c.getName() != null && !c.getName().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Transition a task to FULFILLED, first removing any stale FULFILLED row for
     * the same (episode, field). The unique constraint
     * {@code uq_pending_episode_field(episode_id, field, status)} otherwise
     * rejects a second FULFILLED row when an earlier run already fulfilled the
     * field. We delete-then-flush before the UPDATE because Hibernate would
     * otherwise order the UPDATE ahead of the DELETE and still collide.
     */
    private void markFulfilled(PendingLabTask task, LabResult labResult) {
        Long episodeId = task.getEpisode().getId();
        pendingLabTaskRepository
                .findByEpisodeIdAndFieldAndStatus(episodeId, task.getField(),
                        PendingLabTaskStatus.FULFILLED)
                .filter(existing -> !existing.getId().equals(task.getId()))
                .ifPresent(existing -> {
                    pendingLabTaskRepository.delete(existing);
                    pendingLabTaskRepository.flush();
                });
        task.setStatus(PendingLabTaskStatus.FULFILLED);
        if (labResult != null) {
            task.setFulfilledLabResult(labResult);
        }
        pendingLabTaskRepository.save(task);
    }

    private LabResult getOrCreateLatestLabResult(Long episodeId) {
        var page = labResultRepository.findByEpisodeId(
                episodeId, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));

        if (!page.isEmpty()) {
            return page.getContent().get(0);
        }

        PjiEpisode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        LabResult newLab = LabResult.builder()
                .episode(episode)
                .hematologyTests(new ArrayList<>())
                .fluidAnalysis(new ArrayList<>())
                .biochemicalData(new LinkedHashMap<>())
                .build();
        return labResultRepository.save(newLab);
    }

    /**
     * Collect normalized tokens (row id, row name, biochemical key) for every
     * filled lab datum, so {@link #isFieldPresent} can match against canonical
     * ids and diacritic-insensitive name aliases regardless of how the row was
     * labelled in the form.
     */
    private Set<String> extractPresentFields(List<Map<String, Object>> hematologyTests,
            List<Map<String, Object>> fluidAnalysis,
            Map<String, Object> biochemicalData) {
        Set<String> tokens = new HashSet<>();
        addRowTokens(tokens, hematologyTests);
        addRowTokens(tokens, fluidAnalysis);
        if (biochemicalData != null) {
            for (Map.Entry<String, Object> entry : biochemicalData.entrySet()) {
                if (isBiochemValueFilled(entry.getValue())) {
                    String tok = normalizeToken(entry.getKey());
                    if (!tok.isEmpty()) {
                        tokens.add(tok);
                    }
                }
            }
        }
        return tokens;
    }

    private void addRowTokens(Set<String> tokens, List<Map<String, Object>> rows) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            Object value = row.get("value");
            if (value == null || value.toString().isBlank()) {
                continue;
            }
            for (String key : List.of("id", "name")) {
                String tok = normalizeToken(row.get(key));
                if (!tok.isEmpty()) {
                    tokens.add(tok);
                }
            }
        }
    }

    /** A biochemical entry counts as filled unless its {value} wrapper is empty. */
    private boolean isBiochemValueFilled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Map<?, ?> map && map.containsKey("value")) {
            Object inner = map.get("value");
            return inner != null && !inner.toString().isBlank();
        }
        return !value.toString().isBlank();
    }

    private void removeExisting(List<Map<String, Object>> tests, String id, String name) {
        tests.removeIf(t -> (id != null && id.equals(t.get("id")))
                || (name != null && name.equals(t.get("name"))));
    }
}
