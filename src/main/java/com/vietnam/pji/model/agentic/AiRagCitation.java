package com.vietnam.pji.model.agentic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.constant.SourceType;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_rag_citations")
public class AiRagCitation extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private AiRecommendationRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private AiRecommendationItem item;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 30)
    private SourceType sourceType;

    @Column(name = "source_title", length = 500)
    private String sourceTitle;

    @Column(name = "source_uri", length = 1000)
    private String sourceUri;

    @Column(name = "snippet", columnDefinition = "TEXT")
    private String snippet;

    @Column(name = "relevance_score", precision = 5, scale = 4)
    private BigDecimal relevanceScore;

    @Column(name = "cited_for", length = 500)
    private String citedFor;
}
