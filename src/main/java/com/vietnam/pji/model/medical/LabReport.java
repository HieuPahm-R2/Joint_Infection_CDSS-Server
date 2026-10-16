package com.vietnam.pji.model.medical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lab_reports")
public class LabReport extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Admission episode;

    // Hematology tests stored as JSONB array of test items
    // Each item: { id, name, value, unit, normalRange }
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hematology_tests", columnDefinition = "jsonb")
    private List<Map<String, Object>> hematologyTests;

    // Fluid analysis tests stored as JSONB array of test items
    // Each item: { id, name, value, unit, normalRange }
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fluid_analysis", columnDefinition = "jsonb")
    private List<Map<String, Object>> fluidAnalysis;

    // Biochemical data (JSONB map of key -> {value, unit})
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "biochemical_data", columnDefinition = "jsonb")
    private Map<String, Object> biochemicalData;


    @Version
    @Column(name = "version_no", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
