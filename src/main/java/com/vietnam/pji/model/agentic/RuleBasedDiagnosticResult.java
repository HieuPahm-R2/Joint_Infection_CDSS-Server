package com.vietnam.pji.model.agentic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/** Immutable rule-engine diagnosis calculated from one recommendation snapshot. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rule_based_diagnostic_results")
public class RuleBasedDiagnosticResult extends AbstractEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false, unique = true)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private AiRecommendationRun run;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> itemJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assessment_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> assessmentJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> explanationJson;

}
