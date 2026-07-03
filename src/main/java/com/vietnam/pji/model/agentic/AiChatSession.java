package com.vietnam.pji.model.agentic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.constant.ChatType;
import com.vietnam.pji.model.AbstractEntity;
import com.vietnam.pji.model.medical.PjiEpisode;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_chat_sessions")
public class AiChatSession extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "patient" })
    private PjiEpisode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "episode", "snapshot" })
    private AiRecommendationRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_item_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "run" })
    private AiRecommendationItem currentItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_type", length = 30)
    private ChatType chatType;

    @Column(name = "title", length = 500)
    private String title;

}
