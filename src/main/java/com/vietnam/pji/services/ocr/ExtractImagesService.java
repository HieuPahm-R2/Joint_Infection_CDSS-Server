package com.vietnam.pji.services.ocr;

import com.vietnam.pji.dto.response.ExtractImageJobResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExtractImagesService {

    private final ExtractImagesClient extractImagesClient;

    public ExtractImageJobResponseDTO createJob(MultipartFile[] files, Long episodeId) throws IOException {
        log.info("Creating extract-images job, fileCount={}, episodeId={}", files.length, episodeId);
        Map<String, Object> upstream = extractImagesClient.upload(files);
        return ExtractImageJobResponseDTO.builder()
                .jobId(asString(upstream, "job_id"))
                .status(asString(upstream, "status"))
                .fileCount(asInteger(upstream, "file_count"))
                .build();
    }

    public ExtractImageJobResponseDTO createJob(List<OcrUploadFile> files, Long episodeId) throws IOException {
        log.info("Creating extract-images job from object storage, fileCount={}, episodeId={}",
                files.size(), episodeId);
        Map<String, Object> upstream = extractImagesClient.uploadFiles(files);
        return ExtractImageJobResponseDTO.builder()
                .jobId(asString(upstream, "job_id"))
                .status(asString(upstream, "status"))
                .fileCount(asInteger(upstream, "file_count"))
                .build();
    }

    public ExtractImageJobResponseDTO cancelJob(String jobId) {
        log.info("Cancelling extract-images job {}", jobId);
        Map<String, Object> upstream = extractImagesClient.cancel(jobId);
        return ExtractImageJobResponseDTO.builder()
                .jobId(asString(upstream, "job_id"))
                .status(asString(upstream, "status"))
                .build();
    }

    @SuppressWarnings("unchecked")
    public ExtractImageJobResponseDTO getJob(String jobId) {
        Map<String, Object> upstream = extractImagesClient.getResult(jobId);
        String status = asString(upstream, "status");
        ExtractImageJobResponseDTO.ExtractImageJobResponseDTOBuilder builder = ExtractImageJobResponseDTO.builder()
                .jobId(asString(upstream, "job_id"))
                .status(status);

        if ("completed".equals(status)) {
            Object data = upstream.get("data");
            if (data instanceof Map) {
                builder.extracted((Map<String, Object>) data);
            }
        } else if ("failed".equals(status)) {
            builder.error(asString(upstream, "error"));
        }
        return builder.build();
    }

    private String asString(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : value.toString();
    }

    private Integer asInteger(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number)
            return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
