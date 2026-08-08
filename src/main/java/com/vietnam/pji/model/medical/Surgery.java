package com.vietnam.pji.model.medical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "surgeries")
public class Surgery extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private PjiEpisode episode;

    @Column(name = "surgery_date")
    private LocalDate surgeryDate;

    @Column(name = "surgery_type", length = 255)
    private String surgeryType;

    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @Column(name = "positive_histology")
    private Boolean positiveHistology;

    @Column(name = "intraoperative_purulence")
    private Boolean intraoperativePurulence;

}
