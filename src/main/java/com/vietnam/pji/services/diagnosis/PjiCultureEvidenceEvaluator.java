package com.vietnam.pji.services.diagnosis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Evaluates culture evidence independently from the scoring and report layers. */
@Component
class PjiCultureEvidenceEvaluator {

    private final PjiDiagnosticSnapshotReader snapshotReader;

    PjiCultureEvidenceEvaluator(PjiDiagnosticSnapshotReader snapshotReader) {
        this.snapshotReader = snapshotReader;
    }

    CultureEvidence evaluate(Map<String, Object> snapshot) {
        Object cultureItems = snapshotReader.getNested(snapshot, "culture_results", "items").orElse(null);
        List<?> rawItems = cultureItems instanceof List<?> list ? list : List.of();
        Boolean explicitPerformed = snapshotReader.getNested(snapshot, "culture_results", "performed")
                .map(snapshotReader::asBoolean).orElse(null);
        Boolean performed = explicitPerformed != null ? explicitPerformed
                : cultureItems instanceof List<?> ? Boolean.TRUE : null;
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        Map<String, List<Map<String, Object>>> sensitivitiesByOrganism = new HashMap<>();
        List<String> positiveOrganisms = new ArrayList<>();
        boolean antibioticsBefore = false;
        int positiveCount = 0;

        for (Object raw : rawItems) {
            Map<String, Object> item = snapshotReader.asMap(raw);
            if (item == null) {
                continue;
            }
            antibioticsBefore |= Boolean.TRUE.equals(snapshotReader.asBoolean(item.get("had_antibiotics_before")));
            if (!snapshotReader.isPositiveStatus(item.get("result_status"))) {
                continue;
            }
            positiveCount++;
            String organism = snapshotReader.firstText(item.get("organism_name"), item.get("name"));
            organism = organism == null || organism.isBlank() ? "Không ghi rõ tác nhân" : organism;
            String key = PjiDiagnosticSnapshotReader.normalizeToken(organism);
            counts.merge(key, 1, Integer::sum);
            displayNames.putIfAbsent(key, organism);
            positiveOrganisms.add(organism);
            if (item.get("sensitivities") instanceof List<?> sensitivityList) {
                List<Map<String, Object>> sensitivities = new ArrayList<>();
                for (Object sensitivity : sensitivityList) {
                    Map<String, Object> mapped = snapshotReader.asMap(sensitivity);
                    if (mapped != null) {
                        sensitivities.add(mapped);
                    }
                }
                sensitivitiesByOrganism.putIfAbsent(key, sensitivities);
            }
        }

        String topKey = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey).orElse(null);
        int topCount = topKey != null ? counts.getOrDefault(topKey, 0) : 0;
        String topOrganism = topKey != null ? displayNames.get(topKey) : null;
        List<Map<String, Object>> sensitivities = topKey != null
                ? sensitivitiesByOrganism.getOrDefault(topKey, List.of()) : List.of();
        boolean major = topCount >= 2;
        String majorDetail = performed != Boolean.TRUE ? "Chưa có dữ liệu nuôi cấy."
                : rawItems.isEmpty() ? "Đã ghi nhận thực hiện nuôi cấy nhưng chưa có kết quả đọc được."
                : major ? topCount + " mẫu nuôi cấy dương tính cùng tác nhân: " + topOrganism + "."
                : positiveCount > 0 ? positiveCount + " mẫu dương tính nhưng chưa có ≥2 mẫu cùng tác nhân ("
                        + String.join(", ", positiveOrganisms) + ")."
                : "Có dữ liệu nuôi cấy nhưng không có mẫu dương tính.";
        return new CultureEvidence(performed, rawItems.size(), positiveCount, topCount, major, majorDetail,
                topOrganism, positiveOrganisms, sensitivities, antibioticsBefore);
    }

    record CultureEvidence(Boolean performed, int totalCultureCount, int positiveCount, int topOrganismPositiveCount,
            boolean majorCriteriaMet, String majorDetail, String topOrganism, List<String> positiveOrganisms,
            List<Map<String, Object>> sensitivities, boolean antibioticsBefore) {
        String organismSummary() {
            return positiveOrganisms == null || positiveOrganisms.isEmpty() ? "không có"
                    : String.join(", ", new LinkedHashSet<>(positiveOrganisms));
        }
    }
}
