package com.vietnam.pji.services.diagnosis;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and normalizes the clinical snapshot consumed by diagnostic rules. */
@Component
class PjiDiagnosticSnapshotReader {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:[\\.,]\\d+)?");

    private static final Map<String, LabAlias> LAB_ALIASES = Map.ofEntries(
            Map.entry("serum_CRP", new LabAlias(Set.of("htextracrp"), Set.of("crp"),
                    Set.of("hematology_tests", "biochemical_data", "latest"))),
            Map.entry("serum_ESR", new LabAlias(Set.of("ht7"), Set.of("maulang", "esr", "tocdomaulang"),
                    Set.of("hematology_tests", "latest"))),
            Map.entry("serum_D_Dimer", new LabAlias(Set.of("ht17"), Set.of("ddimer"),
                    Set.of("hematology_tests", "latest"))),
            Map.entry("synovial_WBC", new LabAlias(Set.of("fa3"), Set.of("synovialwbc", "bachcaudich"),
                    Set.of("fluid_analysis", "latest"))),
            Map.entry("synovial_PMN", new LabAlias(Set.of("fa6"), Set.of("synovialpmn", "pmndich"),
                    Set.of("fluid_analysis", "latest"))),
            Map.entry("synovial_CRP", new LabAlias(Set.of("fa5"), Set.of("crpdich", "synovialcrp", "dinhluongcrpdich"),
                    Set.of("fluid_analysis", "latest"))),
            Map.entry("synovial_alpha_defensin", new LabAlias(Set.of("faextraalphadefensin", "ht19"),
                    Set.of("alphadefensin"), Set.of("fluid_analysis", "hematology_tests", "latest"))),
            Map.entry("synovial_LE", new LabAlias(Set.of("faextraleukocyteesterase", "ht15"),
                    Set.of("leukocyteesterase"), Set.of("fluid_analysis", "hematology_tests", "latest"))),
            Map.entry("serum_IL6", new LabAlias(Set.of("ht18"), Set.of("il6"),
                    Set.of("hematology_tests", "latest"))));

    Optional<LabDatum> findLab(Map<String, Object> snapshot, String field) {
        LabAlias alias = LAB_ALIASES.get(field);
        if (alias == null) {
            return Optional.empty();
        }
        return collectLabDatums(snapshot).stream().filter(alias::matches).findFirst();
    }

    Optional<Object> getNested(Map<String, Object> root, String... keys) {
        Object current = root;
        for (String key : keys) {
            Map<String, Object> map = asMap(current);
            if (map == null || !map.containsKey(key)) {
                return Optional.empty();
            }
            current = map.get(key);
        }
        return Optional.ofNullable(current);
    }

    Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String normalized = normalizeToken(value);
        if (Set.of("true", "yes", "y", "1", "co", "duongtinh", "positive").contains(normalized)) {
            return true;
        }
        if (Set.of("false", "no", "n", "0", "khong", "amtinh", "negative").contains(normalized)) {
            return false;
        }
        return null;
    }

    boolean isPositiveStatus(Object status) {
        String normalized = normalizeToken(status);
        return normalized.equals("positive") || normalized.equals("duongtinh") || normalized.equals("pos");
    }

    Boolean anyPositiveOrUnknown(Boolean... values) {
        boolean sawUnknown = false;
        for (Boolean value : values) {
            if (value == Boolean.TRUE) {
                return true;
            }
            if (value == null) {
                sawUnknown = true;
            }
        }
        return sawUnknown ? null : false;
    }

    Boolean qualitativePositive(Object rawValue, Double numeric, double numericThreshold) {
        if (rawValue == null) {
            return null;
        }
        String raw = rawValue.toString().trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        String normalized = normalizeToken(raw);
        if (raw.equals("-") || raw.equals("+") || normalized.contains("negative")
                || normalized.contains("amtinh") || normalized.contains("notdetected")) {
            return false;
        }
        if (raw.contains("++") || raw.contains("+++") || normalized.contains("positive")
                || normalized.contains("duongtinh") || normalized.contains("detected")) {
            return true;
        }
        if (lower.equals("true") || lower.equals("yes")) {
            return true;
        }
        if (lower.equals("false") || lower.equals("no")) {
            return false;
        }
        return numeric != null ? numeric > numericThreshold : null;
    }

    Double numericValue(LabDatum datum) {
        return datum != null ? numericValue(datum.value()) : null;
    }

    Double numericValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        Matcher matcher = NUMBER_PATTERN.matcher(raw.toString());
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    Double dDimerNgMl(LabDatum datum) {
        Double value = numericValue(datum);
        if (value == null) {
            return null;
        }
        String unit = normalizeToken(datum != null ? datum.unit() : null);
        if (unit.contains("ngml") || unit.contains("ngperml")) {
            return value;
        }
        return unit.contains("mgl") || value <= 20.0 ? value * 1000.0 : value;
    }

    TextEvidence textEvidence(Map<String, Object> snapshot, Set<String> contextTokens,
            Set<String> positiveTokens, Set<String> negativeTokens) {
        for (String raw : collectClinicalTexts(snapshot)) {
            String normalized = normalizeToken(raw);
            if (contextTokens.stream().noneMatch(normalized::contains)) {
                continue;
            }
            if (negativeTokens.stream().anyMatch(normalized::contains)) {
                return new TextEvidence(false, "Mô tả ghi nhận âm tính/không có: " + raw);
            }
            if (positiveTokens.stream().anyMatch(normalized::contains) || normalized.matches(".*[>≥]5.*pmn.*")) {
                return new TextEvidence(true, "Mô tả ghi nhận dương tính: " + raw);
            }
        }
        return new TextEvidence(null, "");
    }

    String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    String unitSuffix(LabDatum datum) {
        return datum == null || datum.unit() == null || datum.unit().isBlank() ? "" : " " + datum.unit();
    }

    String formatNumber(double value) {
        if (Math.rint(value) == value) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static String normalizeToken(Object value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.toString(), Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT).replace("đ", "d");
        StringBuilder result = new StringBuilder();
        for (char character : normalized.toCharArray()) {
            if (Character.isLetterOrDigit(character) && character < 128) {
                result.append(character);
            }
        }
        return result.toString();
    }

    private List<LabDatum> collectLabDatums(Map<String, Object> snapshot) {
        Object latestObject = getNested(snapshot, "lab_results", "latest")
                .orElseGet(() -> getNested(snapshot, "lab_results").orElse(null));
        Map<String, Object> latest = asMap(latestObject);
        if (latest == null) {
            return List.of();
        }
        List<LabDatum> datums = new ArrayList<>();
        for (Map.Entry<String, Object> entry : latest.entrySet()) {
            collectLabDatums(entry.getKey(), entry.getKey(), entry.getValue(), datums);
        }
        return datums;
    }

    private void collectLabDatums(String section, String label, Object node, List<LabDatum> output) {
        if (node instanceof List<?> list) {
            for (Object raw : list) {
                Map<String, Object> row = asMap(raw);
                if (row == null) {
                    continue;
                }
                Object value = firstNonNull(row.get("value"), row.get("result"));
                if (isFilled(value)) {
                    String id = text(row.get("id"));
                    String name = text(row.get("name"));
                    output.add(new LabDatum(section, id, name != null ? name : label, value, text(row.get("unit"))));
                }
            }
            return;
        }
        Map<String, Object> map = asMap(node);
        if (map != null) {
            if (map.containsKey("value") || map.containsKey("result")) {
                Object value = firstNonNull(map.get("value"), map.get("result"));
                if (isFilled(value)) {
                    output.add(new LabDatum(section, label, label, value, text(map.get("unit"))));
                }
                return;
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                collectLabDatums(section, entry.getKey(), entry.getValue(), output);
            }
            return;
        }
        if (isFilled(node)) {
            output.add(new LabDatum(section, label, label, node, null));
        }
    }

    private List<String> collectClinicalTexts(Map<String, Object> snapshot) {
        List<String> texts = new ArrayList<>();
        getNested(snapshot, "clinical_records", "infection_assessment", "soft_tissue").map(Object::toString).ifPresent(texts::add);
        getNested(snapshot, "clinical_records", "notations").map(Object::toString).ifPresent(texts::add);
        collectTexts(getNested(snapshot, "surgeries", "items").orElse(null), "findings", null, texts);
        collectTexts(getNested(snapshot, "culture_results", "items").orElse(null), "sample_type", "notes", texts);
        return texts;
    }

    private void collectTexts(Object rawItems, String primaryKey, String secondaryKey, List<String> output) {
        if (!(rawItems instanceof List<?> list)) {
            return;
        }
        for (Object raw : list) {
            Map<String, Object> item = asMap(raw);
            if (item == null) {
                continue;
            }
            String primary = firstText(item.get(primaryKey), secondaryKey != null ? item.get(secondaryKey) : null);
            if (primary != null) {
                output.add(primary);
            }
            if (secondaryKey != null) {
                String secondary = firstText(item.get(secondaryKey));
                if (secondary != null) {
                    output.add(secondary);
                }
            }
        }
    }

    private boolean isFilled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String string) {
            return !string.isBlank();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(this::isFilled);
        }
        return true;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value != null ? value.toString() : null;
    }

    private record LabAlias(Set<String> ids, Set<String> names, Set<String> sections) {
        boolean matches(LabDatum datum) {
            String id = normalizeToken(datum.id());
            String name = normalizeToken(datum.name());
            String section = datum.section();
            boolean sectionMatches = sections.contains(section) || (sections.contains("latest")
                    && !Set.of("hematology_tests", "fluid_analysis", "biochemical_data").contains(section));
            return sectionMatches && (ids.contains(id)
                    || names.stream().anyMatch(alias -> name.contains(alias) || id.contains(alias)));
        }
    }

    record LabDatum(String section, String id, String name, Object value, String unit) {
        String label() {
            return name != null && !name.isBlank() ? name : id;
        }
    }

    record TextEvidence(Boolean result, String detail) {
    }
}
