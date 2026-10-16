package com.vietnam.pji.model.agentic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.model.AbstractEntity;
import com.vietnam.pji.model.medical.Admission;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clinical_snapshots")
public class ClinicalSnapshot extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Admission episode;

    @Column(name = "snapshot_no")
    private Integer snapshotNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_data_json", columnDefinition = "jsonb")
    private String snapshotDataJson;

    @Column(name = "data_completeness_score", precision = 5, scale = 2)
    private BigDecimal dataCompletenessScore;
}
