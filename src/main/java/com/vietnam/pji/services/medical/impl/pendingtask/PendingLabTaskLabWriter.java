package com.vietnam.pji.services.medical.impl.pendingtask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.medical.LabResult;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.LabResultRepository;

import lombok.RequiredArgsConstructor;

/**
 * Writes a single quick-entry value into the episode's current {@code LabResult}
 * row set.
 *
 * <p>
 * Owns the payload shape the frontend and the Rag_Agentic completeness check
 * both read: a {@code {id, name, value, unit, normalRange}} entry in
 * {@code hematologyTests}/{@code fluidAnalysis}, and a {@code {value, unit}}
 * entry under the canonical lowercase key in {@code biochemicalData}. That shape
 * used to live inline in {@code PendingLabTaskServiceImpl}, entangled with task
 * lifecycle rules.
 */
@Component
@RequiredArgsConstructor
public class PendingLabTaskLabWriter {

    private final LabResultRepository labResultRepository;
    private final EpisodeRepository episodeRepository;
    private final LabFieldCatalog catalog;

    /**
     * Records {@code value} for {@code field} on the episode's most recent lab
     * result, creating that lab result when the episode has none yet.
     *
     * @return the persisted lab result that now carries the value
     */
    @Transactional
    public LabResult writeValue(Long episodeId, String field, Object value, String unit) {
        LabFieldCatalog.LabFieldSpec spec = catalog.specFor(field);
        LabResult labResult = getOrCreateLatestLabResult(episodeId);

        switch (spec.section()) {
            case "hematology" -> {
                List<Map<String, Object>> tests = labResult.getHematologyTests() == null
                        ? new ArrayList<>()
                        : labResult.getHematologyTests();
                removeExisting(tests, spec.id(), spec.name());
                tests.add(rowEntry(spec, value, unit));
                labResult.setHematologyTests(tests);
            }
            case "fluid" -> {
                List<Map<String, Object>> tests = labResult.getFluidAnalysis() == null
                        ? new ArrayList<>()
                        : labResult.getFluidAnalysis();
                removeExisting(tests, spec.id(), spec.name());
                tests.add(rowEntry(spec, value, unit));
                labResult.setFluidAnalysis(tests);
            }
            case "biochemical" -> {
                Map<String, Object> data = labResult.getBiochemicalData() == null
                        ? new LinkedHashMap<>()
                        : labResult.getBiochemicalData();
                // Store under the canonical lowercase key so the frontend id-mapping
                // and the AI completeness check both resolve it; falls back to the
                // spec name for unmapped fields.
                Map<String, Object> biochemEntry = new LinkedHashMap<>();
                biochemEntry.put("value", value);
                biochemEntry.put("unit", resolvedUnit(spec, unit));
                data.put(catalog.biochemKey(field, spec), biochemEntry);
                labResult.setBiochemicalData(data);
            }
            default -> throw new IllegalStateException(
                    "Unsupported lab section '" + spec.section() + "' for field " + field);
        }

        return labResultRepository.save(labResult);
    }

    /**
     * Self-describing row: id + name + normalRange let the frontend merge into
     * the canonical default row when one exists, or render the AI-proposed row
     * via the {@code ht_extra_/fa_extra_} namespace without crashing.
     */
    private Map<String, Object> rowEntry(LabFieldCatalog.LabFieldSpec spec, Object value, String unit) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", spec.id());
        entry.put("name", spec.name());
        entry.put("value", value);
        entry.put("unit", resolvedUnit(spec, unit));
        entry.put("normalRange", spec.normalRange());
        return entry;
    }

    private String resolvedUnit(LabFieldCatalog.LabFieldSpec spec, String unit) {
        return unit != null ? unit : spec.defaultUnit();
    }

    private void removeExisting(List<Map<String, Object>> tests, String id, String name) {
        tests.removeIf(test -> (id != null && id.equals(test.get("id")))
                || (name != null && name.equals(test.get("name"))));
    }

    private LabResult getOrCreateLatestLabResult(Long episodeId) {
        var latest = labResultRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId);
        if (latest.isPresent()) {
            return latest.get();
        }

        PjiEpisode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        LabResult newLabResult = LabResult.builder()
                .episode(episode)
                .hematologyTests(new ArrayList<>())
                .fluidAnalysis(new ArrayList<>())
                .biochemicalData(new LinkedHashMap<>())
                .build();
        return labResultRepository.save(newLabResult);
    }
}
