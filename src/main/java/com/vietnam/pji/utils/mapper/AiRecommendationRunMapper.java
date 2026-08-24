package com.vietnam.pji.utils.mapper;

import com.vietnam.pji.dto.response.AiRecommendationRunDTO;
import com.vietnam.pji.model.agentic.AiRecommendationRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Maps the {@link AiRecommendationRun} JPA entity to its flat response DTO.
 *
 * <p>Reads {@code episode.id} / {@code snapshot.id} only — never any other
 * lazy field — so the mapper can be safely called outside a Hibernate session.
 * Callers must ensure {@code episode_id} / {@code snapshot_id} have already
 * been materialized (they sit on the run row itself, so this is trivially true
 * for any persisted run).
 */
@Mapper(config = DefaultConfigMapper.class)
public interface AiRecommendationRunMapper {

    @Mapping(target = "episodeId", source = "episode.id")
    @Mapping(target = "snapshotId", source = "snapshot.id")
    @Mapping(target = "triggerType", source = "triggerType", qualifiedByName = "enumToString")
    @Mapping(target = "recommendationScope", source = "recommendationScope", qualifiedByName = "enumToString")
    @Mapping(target = "status", source = "status", qualifiedByName = "enumToString")
    AiRecommendationRunDTO toDto(AiRecommendationRun run);

    @Named("enumToString")
    default String enumToString(Enum<?> e) {
        return e != null ? e.name() : null;
    }
}
