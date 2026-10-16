package com.vietnam.pji.services.medical.impl.pendingtask;

import com.vietnam.pji.constant.PendingLabTaskStatus;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.agentic.PendingLabTask;
import com.vietnam.pji.model.medical.ClinicalAssessment;
import com.vietnam.pji.model.medical.SpecimenCulture;
import com.vietnam.pji.model.medical.LabReport;
import com.vietnam.pji.model.medical.MedicalHistory;
import com.vietnam.pji.model.medical.Admission;
import com.vietnam.pji.model.medical.Surgery;
import com.vietnam.pji.repository.AdmissionRepository;
import com.vietnam.pji.repository.LabReportRepository;
import com.vietnam.pji.repository.MedicalHistoryRepository;
import com.vietnam.pji.repository.PendingLabTaskRepository;
import com.vietnam.pji.repository.SurgeryRepository;
import com.vietnam.pji.repository.ai.AiRecommendationRunRepository;
import com.vietnam.pji.repository.medical.ClinicalAssessmentRepository;
import com.vietnam.pji.repository.medical.SpecimenCultureRepository;
import com.vietnam.pji.services.medical.PendingLabTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the pending-completeness task lifecycle: what opens a task, who it is
 * assigned to, and when it closes.
 *
 * <p>
 * The two bodies of knowledge this class used to carry inline now live with
 * their own owners: {@link LabFieldCatalog} (field identity, storage section,
 * aliases) and {@link LabFieldFulfillmentDetector} (has the clinician already
 * answered this field?), and {@link PendingLabTaskLabWriter} performs the lab
 * write behind {@link #fulfillByQuickEntry}. What remains here is orchestration
 * plus the task state machine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PendingLabTaskServiceImpl implements PendingLabTaskService {

    private final PendingLabTaskRepository pendingLabTaskRepository;
    private final LabReportRepository labResultRepository;
    private final AdmissionRepository episodeRepository;
    private final AiRecommendationRunRepository aiRecommendationRunRepository;
    private final ClinicalAssessmentRepository clinicalRecordRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final SpecimenCultureRepository cultureResultRepository;
    private final SurgeryRepository surgeryRepository;
    private final LabFieldCatalog catalog;
    private final LabFieldFulfillmentDetector fulfillmentDetector;
    private final PendingLabTaskLabWriter labWriter;

    @Override
    @Transactional(readOnly = true)
    public List<PendingLabTask> getMyPendingTasks(Long userId) {
        // Return PENDING + FULFILLED (not DISMISSED) so the tooltip and the
        // in-episode tab can show per-episode progress, not just the open items.
        List<PendingLabTask> tasks = pendingLabTaskRepository
                .findByAssignedToUserIdAndStatusInOrderByCreatedAtDesc(
                        userId,
                        List.of(PendingLabTaskStatus.PENDING, PendingLabTaskStatus.FULFILLED));
        tasks.forEach(this::initializeGraph);
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
        PendingLabTask task = requireTask(taskId);
        task.setStatus(PendingLabTaskStatus.DISMISSED);
        pendingLabTaskRepository.save(task);
    }

    @Override
    @Transactional
    public void fulfillByQuickEntry(Long taskId, Object value, String unit) {
        PendingLabTask task = requireTask(taskId);

        if (task.getStatus() != PendingLabTaskStatus.PENDING) {
            return;
        }

        Long episodeId = task.getEpisode().getId();
        String field = task.getField();
        LabReport saved = labWriter.writeValue(episodeId, field, value, unit);

        // Mark task as fulfilled (drops any stale FULFILLED row for the field)
        markFulfilled(task, saved);

        log.info("Quick-entry fulfilled: taskId={}, field={}, episodeId={}", taskId, field, episodeId);
    }

    @Override
    @Transactional
    public void createFromCompleteness(Long episodeId, Long patientId, Long userId,
            Long runId, List<Map<String, Object>> missingItems) {
        if (missingItems == null || missingItems.isEmpty()) {
            return;
        }

        Admission episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));

        for (Map<String, Object> item : missingItems) {
            String field = (String) item.get("field");
            if (!catalog.isEligibleField(field)) {
                log.warn("Skipping unsupported completeness field: episodeId={}, field={}", episodeId, field);
                continue;
            }
            String inputType = (String) item.get("input_type");
            String section = (String) item.get("section");
            String unit = (String) item.get("unit");
            String normalRange = (String) item.get("normal_range");

            Optional<PendingLabTask> existing = pendingLabTaskRepository
                    .findByEpisodeIdAndFieldAndStatus(episodeId, field, PendingLabTaskStatus.PENDING);
            if (existing.isPresent()) {
                refreshExistingTask(existing.get(), userId, runId, inputType, section, unit, normalRange);
                continue;
            }

            pendingLabTaskRepository.save(PendingLabTask.builder()
                    .episode(episode)
                    .patient(episode.getPatient())
                    .assignedToUserId(userId)
                    .field(field)
                    .category((String) item.get("category"))
                    .importance((String) item.get("importance"))
                    .message((String) item.get("message"))
                    .inputType(inputType)
                    .section(section)
                    .unit(unit)
                    .normalRange(normalRange)
                    .status(PendingLabTaskStatus.PENDING)
                    .createdFromRunId(runId)
                    .build());
        }

        // A non-null userId means this is the doctor's explicit "Lưu nhắc nhở"
        // action (the async worker passes null) — persist the saved marker so
        // the button stays disabled across reloads/devices.
        markRunTasksSaved(userId, runId);

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

        if (pendingTasks.isEmpty()) {
            return;
        }

        Set<String> presentTokens = fulfillmentDetector
                .extractPresentFields(hematologyTests, fluidAnalysis, biochemicalData);

        LabReport labResult = labResultRepository.findById(labResultId).orElse(null);

        for (PendingLabTask task : pendingTasks) {
            if (fulfillmentDetector.isLabFieldPresent(task.getField(), presentTokens)) {
                markFulfilled(task, labResult);
                log.info("Auto-fulfilled pending task: id={}, field={}", task.getId(), task.getField());
            }
        }
    }

    @Override
    @Transactional
    public void autoFulfillClinicalForEpisode(Long episodeId) {
        List<PendingLabTask> pendingTasks = pendingLabTaskRepository
                .findByEpisodeIdAndStatus(episodeId, PendingLabTaskStatus.PENDING);
        if (pendingTasks.isEmpty()) {
            return;
        }

        // Only load what the clinical/culture checks below actually need.
        ClinicalAssessment clinicalRecord = needsField(pendingTasks,
                "sinus_tract", "infection_type", "implant_stability")
                        ? clinicalRecordRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId).orElse(null)
                        : null;
        MedicalHistory medicalHistory = needsField(pendingTasks, "allergies")
                ? medicalHistoryRepository.findByEpisodeId(episodeId).orElse(null)
                : null;
        List<SpecimenCulture> cultures = needsField(pendingTasks, "culture_results")
                ? cultureResultRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId)
                : List.of();
        List<Surgery> surgeries = needsField(pendingTasks, "positive_histology")
                ? surgeryRepository.findByEpisodeIdOrderBySurgeryDateAsc(episodeId)
                : List.of();

        for (PendingLabTask task : pendingTasks) {
            if (fulfillmentDetector.isClinicalFieldSatisfied(
                    task.getField(), clinicalRecord, medicalHistory, cultures, surgeries)) {
                markFulfilled(task, null);
                log.info("Auto-fulfilled clinical pending task: id={}, field={}",
                        task.getId(), task.getField());
            }
        }
    }

    private boolean needsField(List<PendingLabTask> tasks, String... fields) {
        Set<String> wanted = Set.of(fields);
        return tasks.stream().anyMatch(task -> wanted.contains(task.getField()));
    }

    /**
     * Backfills render metadata on tasks the async worker created before the
     * richer payload was available, and claims unassigned tasks for the user who
     * just saved the episode.
     */
    private void refreshExistingTask(PendingLabTask task, Long userId, Long runId,
            String inputType, String section, String unit, String normalRange) {
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
    }

    private void markRunTasksSaved(Long userId, Long runId) {
        if (userId == null || runId == null) {
            return;
        }
        aiRecommendationRunRepository.findById(runId).ifPresent(run -> {
            if (!run.isPendingTasksSaved()) {
                run.setPendingTasksSaved(true);
                aiRecommendationRunRepository.save(run);
            }
        });
    }

    private PendingLabTask requireTask(Long taskId) {
        return pendingLabTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Pending task not found"));
    }

    private void initializeGraph(PendingLabTask task) {
        Hibernate.initialize(task.getEpisode());
        if (task.getEpisode() != null) {
            Hibernate.initialize(task.getEpisode().getPatient());
        }
        Hibernate.initialize(task.getPatient());
        if (task.getFulfilledLabResult() != null) {
            Hibernate.initialize(task.getFulfilledLabResult());
            Hibernate.initialize(task.getFulfilledLabResult().getEpisode());
            if (task.getFulfilledLabResult().getEpisode() != null) {
                Hibernate.initialize(task.getFulfilledLabResult().getEpisode().getPatient());
            }
        }
    }

    /**
     * Transition a task to FULFILLED, first removing any stale FULFILLED row for
     * the same (episode, field). The unique constraint
     * {@code uq_pending_episode_field(episode_id, field, status)} otherwise
     * rejects a second FULFILLED row when an earlier run already fulfilled the
     * field. We delete-then-flush before the UPDATE because Hibernate would
     * otherwise order the UPDATE ahead of the DELETE and still collide.
     */
    private void markFulfilled(PendingLabTask task, LabReport labResult) {
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

}
