package com.vietnam.pji.services.episode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

public interface AdmissionSnapshotAssemblerService {

    SnapshotBuildResult buildSnapshot(Long episodeId);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class SnapshotBuildResult {
        private Map<String, Object> snapshotDataJson;
        private BigDecimal completenessScore;
    }
}
