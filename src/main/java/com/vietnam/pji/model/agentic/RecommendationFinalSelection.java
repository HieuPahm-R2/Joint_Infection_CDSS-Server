package com.vietnam.pji.model.agentic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vietnam.pji.model.AbstractEntity;
import com.vietnam.pji.constant.RecommendationScope;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.model.medical.PjiEpisode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recommendation_final_selections")
public class RecommendationFinalSelection extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    @JsonIgnore
    private PjiEpisode episode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false, unique = true)
    @JsonIgnore
    private AiRecommendationRun run;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_scope", nullable = false, length = 30)
    @Builder.Default
    private RecommendationScope recommendationScope = RecommendationScope.LEGACY_COMBINED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_by_user_id", nullable = false)
    @JsonIgnore
    private User selectedBy;

    @Column(name = "selected_at", nullable = false)
    private Instant selectedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
