package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImagingStudyRequestDTO {

    @NotNull(message = "episodeId must not be null")
    private Long episodeId;

    private String type;

    private String findings;

    private String fileMetadata;

    /**
     * Stable MinIO bucket — captured at upload time. Persist instead of a presigned
     * URL.
     */
    private String bucket;

    /**
     * Stable MinIO object key — captured at upload time. Persist instead of a
     * presigned URL.
     */
    private String objectKey;
}
