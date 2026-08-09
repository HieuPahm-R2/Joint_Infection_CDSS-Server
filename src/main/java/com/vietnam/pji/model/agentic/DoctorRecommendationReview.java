package com.vietnam.pji.model.agentic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.constant.ReviewStatus;
import com.vietnam.pji.model.AbstractEntity;
import com.vietnam.pji.model.medical.PjiEpisode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "doctor_recommendation_reviews")
public class DoctorRecommendationReview extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private PjiEpisode episode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false, unique = true)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private AiRecommendationRun run;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20, nullable = false)
    private ReviewStatus reviewStatus;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modification_json", columnDefinition = "jsonb")
    private Map<String, Object> modificationJson;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * The doctor's own final diagnosis captured in the "Chẩn đoán bác sĩ" step:
     * {pji_conclusion, infection_classification, primary_diagnosis,
     * clinical_reasoning, identified_organism}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "doctor_diagnosis_json", columnDefinition = "jsonb")
    private Map<String, Object> doctorDiagnosisJson;

    /**
     * Per-criterion AI-vs-doctor agreement (booleans) plus an overall
     * agreement_rate (0-100), computed at save time. Drives the comparison
     * table, consensus statistics, and future model learning from the
     * doctor's final word.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "agreement_json", columnDefinition = "jsonb")
    private Map<String, Object> agreementJson;

    /** Exactly one review version per episode may be the signed final version. */
    @Column(name = "is_final_decision", nullable = false)
    @Builder.Default
    private boolean finalDecision = false;

    @OneToOne(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private DoctorFinalDecision doctorFinalDecision;

    @OneToOne(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private PharmacistFinalDecision pharmacistFinalDecision;

}
