package com.vietnam.pji.model.medical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vietnam.pji.constant.CultureStatus;
import com.vietnam.pji.model.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "culture_results")
public class CultureResult extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private PjiEpisode episode;

    @Column(name = "incubation_days")
    private Integer incubationDays;

    @Column(name = "name", length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", columnDefinition = "sample_result_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CultureStatus result;

    @Column(name = "antibioticed")
    private Boolean antibioticed;

    @Column(name = "days_off_antibio")
    private Integer daysOffAntibio;

    @Column(name = "gram_type", length = 20)
    private String gramType;

    @Column(name = "sample_type", length = 100)
    private String sampleType;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

}
