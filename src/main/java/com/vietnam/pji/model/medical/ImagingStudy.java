package com.vietnam.pji.model.medical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "imaging_studies")
public class ImagingStudy implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Admission episode;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_metadata", columnDefinition = "jsonb")
    private String fileMetadata;

    /**
     * S3/MinIO bucket the original file lives in. Used to regenerate presigned URLs
     * at read time.
     */
    @Column(name = "bucket", length = 200)
    private String bucket;

    /** S3/MinIO object key. Used to regenerate presigned URLs at read time. */
    @Column(name = "object_key", length = 500)
    private String objectKey;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;
}
