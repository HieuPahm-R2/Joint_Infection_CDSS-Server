package com.vietnam.pji.config.properties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload-session")
public class UploadSessionProperties {

    private Duration ttl = Duration.ofMinutes(5);
    private long maxFileSizeBytes = 5L * 1024L * 1024L;
    private int maxFiles = 10;
    private String bucket = "clinical-upload-sessions";
    private String publicWebUrl;
    private Set<String> allowedContentTypes = new LinkedHashSet<>(
            Set.of("image/jpeg", "image/png", "image/heic", "image/heif"));
}
