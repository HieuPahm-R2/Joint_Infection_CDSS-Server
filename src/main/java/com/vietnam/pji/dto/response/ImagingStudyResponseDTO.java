package com.vietnam.pji.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * Response shape for ImagingStudy that embeds a freshly-signed URL.
 *
 * The `url` field is generated at response time by the service using the current MinIO
 * presigner client, so it never goes stale from the persisted-data side. Clients receive
 * a URL valid for the configured presigned-expiry window (default 60 minutes).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImagingStudyResponseDTO {
    private Long id;
    private Long episodeId;
    private String type;
    private String findings;
    private String fileMetadata;
    private String bucket;
    private String objectKey;
    /** Fresh presigned URL — generated per response. Do NOT persist this anywhere. */
    private String url;
    private Date createdAt;
}
