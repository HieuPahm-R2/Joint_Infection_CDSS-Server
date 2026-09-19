package com.vietnam.pji.services.upload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import com.vietnam.pji.config.properties.UploadSessionProperties;
import com.vietnam.pji.constant.UploadSessionStatus;
import com.vietnam.pji.dto.request.UploadSessionCleanupMessage;
import com.vietnam.pji.dto.request.UploadSessionQueueMessage;
import com.vietnam.pji.dto.response.CreateUploadSessionResponseDTO;
import com.vietnam.pji.dto.response.ExtractImageJobResponseDTO;
import com.vietnam.pji.dto.response.PresignedUploadResponseDTO;
import com.vietnam.pji.dto.response.UploadSessionCompleteResponseDTO;
import com.vietnam.pji.dto.response.UploadSessionEventDTO;
import com.vietnam.pji.dto.response.UploadSessionValidationResponseDTO;
import com.vietnam.pji.exception.BusinessException;
import com.vietnam.pji.exception.UploadSessionGoneException;
import com.vietnam.pji.exception.UploadSessionUnauthorizedException;
import com.vietnam.pji.message.UploadSessionEventPublisher;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.model.upload.UploadSession;
import com.vietnam.pji.model.upload.UploadSessionFile;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.upload.UploadSessionRepository;
import com.vietnam.pji.repository.upload.UploadSessionRepository.MutationResult;
import com.vietnam.pji.services.medical.PatientService;
import com.vietnam.pji.services.ocr.ExtractImagesService;
import com.vietnam.pji.services.ocr.OcrUploadFile;
import com.vietnam.pji.utils.MinioChannel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadSessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> HEIF_BRANDS = Set.of(
            "heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1");

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadSessionProperties properties;
    private final PatientService patientService;
    private final EpisodeRepository episodeRepository;
    private final MinioChannel minioChannel;
    private final UploadSessionEventPublisher eventPublisher;
    private final ExtractImagesService extractImagesService;

    public CreateUploadSessionResponseDTO create(
            Long patientId,
            Long episodeId,
            Long doctorId,
            String requestBaseUrl) {
        if (doctorId == null) {
            throw new UploadSessionUnauthorizedException("Authentication is required");
        }
        validatePatientAndEpisode(patientId, episodeId);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getTtl());
        UUID sessionId = UUID.randomUUID();
        String rawToken = generateToken();

        UploadSession session = UploadSession.builder()
                .sessionId(sessionId)
                .tokenHash(hashToken(rawToken))
                .patientId(patientId)
                .episodeId(episodeId)
                .doctorId(doctorId)
                .createdAt(now)
                .expiresAt(expiresAt)
                .status(UploadSessionStatus.PENDING)
                .processing(false)
                .files(List.of())
                .build();
        uploadSessionRepository.create(session, properties.getTtl());

        String baseUrl = StringUtils.hasText(properties.getPublicWebUrl())
                ? properties.getPublicWebUrl()
                : requestBaseUrl;
        String qrPayload = UriComponentsBuilder.fromUriString(stripTrailingSlash(baseUrl))
                .pathSegment("m", "upload", sessionId.toString())
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        log.info("UPLOAD_SESSION_CREATED sessionId={} patientId={} episodeId={} doctorId={} expiresAt={}",
                sessionId, patientId, episodeId, doctorId, expiresAt);
        return new CreateUploadSessionResponseDTO(sessionId, qrPayload, expiresAt);
    }

    public UploadSessionValidationResponseDTO validate(UUID sessionId, String rawToken) {
        UploadSession session = requirePendingSession(sessionId, rawToken);
        return new UploadSessionValidationResponseDTO(
                sessionId,
                session.expiresAt(),
                properties.getMaxFiles(),
                properties.getMaxFileSizeBytes(),
                Set.copyOf(properties.getAllowedContentTypes()));
    }

    public PresignedUploadResponseDTO presign(
            UUID sessionId,
            String rawToken,
            String originalName,
            String contentType,
            long declaredSize) {
        UploadSession session = requirePendingSession(sessionId, rawToken);
        String normalizedType = normalizeContentType(contentType);
        validateDeclaredFile(normalizedType, declaredSize);

        UUID fileId = UUID.randomUUID();
        String safeName = sanitizeFilename(originalName);
        String objectKey = "upload-sessions/" + sessionId + "/" + fileId + "/" + safeName;
        UploadSessionFile file = UploadSessionFile.builder()
                .fileId(fileId)
                .originalName(safeName)
                .contentType(normalizedType)
                .declaredSize(declaredSize)
                .bucket(properties.getBucket())
                .objectKey(objectKey)
                .createdAt(Instant.now())
                .build();

        minioChannel.initPrivateBucket(properties.getBucket());
        MutationResult stored = uploadSessionRepository.addFile(
                sessionId,
                session.tokenHash(),
                file,
                properties.getMaxFiles());
        handleCapabilityMutation(stored);

        long expirySeconds = remainingSeconds(session);
        String uploadUrl = minioChannel.presignedPutUrl(file.bucket(), file.objectKey(), expirySeconds);
        log.info("UPLOAD_SESSION_FILE_PRESIGNED sessionId={} fileId={} type={} declaredSize={}",
                sessionId, fileId, normalizedType, declaredSize);
        return new PresignedUploadResponseDTO(
                fileId,
                uploadUrl,
                "PUT",
                normalizedType,
                Instant.now().plusSeconds(expirySeconds));
    }

    public UploadSessionCompleteResponseDTO complete(UUID sessionId, String rawToken) {
        UploadSession session = requirePendingSession(sessionId, rawToken);
        if (session.files() == null || session.files().isEmpty()) {
            throw new BusinessException("At least one uploaded image is required");
        }

        for (UploadSessionFile file : session.files()) {
            verifyStoredObject(sessionId, file);
        }

        Instant completedAt = Instant.now();
        MutationResult transitioned = uploadSessionRepository.markUploaded(
                sessionId,
                session.tokenHash(),
                completedAt);
        handleCapabilityMutation(transitioned);

        try {
            eventPublisher.publishCompleted(new UploadSessionQueueMessage(sessionId, completedAt));
        } catch (RuntimeException ex) {
            uploadSessionRepository.restorePending(sessionId);
            throw ex;
        }

        log.info("UPLOAD_SESSION_COMPLETED sessionId={} patientId={} episodeId={} doctorId={} fileCount={}",
                sessionId, session.patientId(), session.episodeId(), session.doctorId(), session.files().size());
        return new UploadSessionCompleteResponseDTO(
                sessionId,
                UploadSessionStatus.UPLOADED,
                session.files().size());
    }

    public Optional<UploadSessionEventDTO> processCompletedSession(UUID sessionId) {
        MutationResult claimed = uploadSessionRepository.claimProcessing(sessionId);
        if (claimed == MutationResult.ALREADY_PROCESSING
                || claimed == MutationResult.NOT_FOUND
                || claimed == MutationResult.INVALID_STATUS) {
            return Optional.empty();
        }
        if (claimed != MutationResult.SUCCESS) {
            throw new IllegalStateException("Unable to claim upload session " + sessionId + ": " + claimed);
        }

        try {
            UploadSession session = uploadSessionRepository.find(sessionId)
                    .orElseThrow(() -> new UploadSessionGoneException("Upload session expired"));
            List<OcrUploadFile> ocrFiles = new ArrayList<>();
            for (UploadSessionFile file : session.files()) {
                byte[] content = minioChannel.download(file.bucket(), file.objectKey());
                ocrFiles.add(new OcrUploadFile(file.originalName(), file.contentType(), content));
            }

            ExtractImageJobResponseDTO job = extractImagesService.createJob(ocrFiles, session.episodeId());
            if (!StringUtils.hasText(job.getJobId())) {
                throw new IllegalStateException("OCR service did not return a jobId");
            }

            uploadSessionRepository.markConsumed(sessionId, job.getJobId());
            UploadSessionEventDTO event = buildEvent(session, job.getJobId());
            scheduleCleanup(session);

            log.info("UPLOAD_SESSION_CONSUMED sessionId={} patientId={} episodeId={} doctorId={} jobId={}",
                    sessionId, session.patientId(), session.episodeId(), session.doctorId(), job.getJobId());
            return Optional.of(event);
        } catch (Exception ex) {
            uploadSessionRepository.releaseProcessing(sessionId);
            throw new IllegalStateException("Unable to process upload session " + sessionId, ex);
        }
    }

    public Optional<UploadSessionEventDTO> replayEventForDoctor(UUID sessionId, Long doctorId) {
        return uploadSessionRepository.find(sessionId)
                .filter(session -> session.doctorId().equals(doctorId))
                .filter(session -> session.status() == UploadSessionStatus.CONSUMED)
                .filter(session -> StringUtils.hasText(session.jobId()))
                .map(session -> buildEvent(session, session.jobId()));
    }

    public boolean belongsToDoctor(UUID sessionId, Long doctorId) {
        return doctorId != null && uploadSessionRepository.find(sessionId)
                .map(session -> doctorId.equals(session.doctorId()))
                .orElse(false);
    }

    public boolean exists(UUID sessionId) {
        return uploadSessionRepository.find(sessionId).isPresent();
    }

    public void cleanup(UploadSessionCleanupMessage message) {
        if (message == null || message.objects() == null) return;
        for (UploadSessionCleanupMessage.ObjectRef object : message.objects()) {
            try {
                minioChannel.deleteObject(object.bucket(), object.objectKey());
            } catch (RuntimeException ex) {
                log.warn("Upload-session cleanup failed sessionId={} objectKey={}: {}",
                        message.sessionId(), object.objectKey(), ex.getMessage());
            }
        }
        log.info("UPLOAD_SESSION_OBJECTS_CLEANED sessionId={} objectCount={}",
                message.sessionId(), message.objects().size());
    }

    private void validatePatientAndEpisode(Long patientId, Long episodeId) {
        patientService.getById(patientId);
        PjiEpisode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new BusinessException("Episode not found"));
        if (episode.getPatient() == null || !patientId.equals(episode.getPatient().getId())) {
            throw new BusinessException("Episode does not belong to the selected patient");
        }
    }

    private UploadSession requirePendingSession(UUID sessionId, String rawToken) {
        UploadSession session = uploadSessionRepository.find(sessionId)
                .orElseThrow(() -> new UploadSessionGoneException("Upload session expired"));
        if (!tokenMatches(rawToken, session.tokenHash())) {
            throw new UploadSessionUnauthorizedException("Invalid upload token");
        }
        if (session.status() != UploadSessionStatus.PENDING || !Instant.now().isBefore(session.expiresAt())) {
            throw new UploadSessionGoneException("Upload session is no longer available");
        }
        return session;
    }

    private void validateDeclaredFile(String contentType, long declaredSize) {
        if (!properties.getAllowedContentTypes().contains(contentType)) {
            throw new BusinessException("Unsupported image content type");
        }
        if (declaredSize <= 0 || declaredSize > properties.getMaxFileSizeBytes()) {
            throw new BusinessException("Image size must be between 1 byte and "
                    + properties.getMaxFileSizeBytes() + " bytes");
        }
    }

    private void verifyStoredObject(UUID sessionId, UploadSessionFile file) {
        try {
            MinioChannel.StoredObjectMetadata actual = minioChannel.statObject(file.bucket(), file.objectKey());
            String actualType = normalizeContentType(actual.contentType());
            if (actual.size() != file.declaredSize()
                    || actual.size() <= 0
                    || actual.size() > properties.getMaxFileSizeBytes()
                    || !file.contentType().equals(actualType)
                    || !properties.getAllowedContentTypes().contains(actualType)) {
                deleteRejectedObject(sessionId, file);
                throw new BusinessException("Uploaded image metadata does not match the signed request");
            }
            byte[] prefix = minioChannel.readPrefix(file.bucket(), file.objectKey(), 32);
            if (!matchesMagic(prefix, actualType)) {
                deleteRejectedObject(sessionId, file);
                throw new BusinessException("Uploaded file content is not a valid " + actualType + " image");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException("One or more signed images have not finished uploading");
        }
    }

    private void deleteRejectedObject(UUID sessionId, UploadSessionFile file) {
        uploadSessionRepository.removeFile(sessionId, file.fileId());
        try {
            minioChannel.deleteObject(file.bucket(), file.objectKey());
        } catch (RuntimeException ex) {
            log.warn("Unable to delete rejected upload object {}: {}", file.objectKey(), ex.getMessage());
        }
    }

    private boolean matchesMagic(byte[] prefix, String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return prefix.length >= 3
                    && (prefix[0] & 0xff) == 0xff
                    && (prefix[1] & 0xff) == 0xd8
                    && (prefix[2] & 0xff) == 0xff;
        }
        if ("image/png".equals(contentType)) {
            byte[] signature = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            return prefix.length >= signature.length
                    && MessageDigest.isEqual(signature, java.util.Arrays.copyOf(prefix, signature.length));
        }
        if ("image/heic".equals(contentType) || "image/heif".equals(contentType)) {
            if (prefix.length < 12
                    || prefix[4] != 'f'
                    || prefix[5] != 't'
                    || prefix[6] != 'y'
                    || prefix[7] != 'p') {
                return false;
            }
            for (int offset = 8; offset + 4 <= prefix.length; offset += 4) {
                String brand = new String(prefix, offset, 4, StandardCharsets.US_ASCII)
                        .toLowerCase(Locale.ROOT);
                if (HEIF_BRANDS.contains(brand)) {
                    return true;
                }
            }
        }
        return false;
    }

    private UploadSessionEventDTO buildEvent(UploadSession session, String jobId) {
        long previewExpirySeconds = Math.max(1L, properties.getTtl().toSeconds());
        List<UploadSessionEventDTO.UploadedImageDTO> images = session.files().stream()
                .map(file -> new UploadSessionEventDTO.UploadedImageDTO(
                        file.fileId(),
                        file.originalName(),
                        file.contentType(),
                        file.declaredSize(),
                        minioChannel.presignedGetUrl(file.bucket(), file.objectKey(), previewExpirySeconds)))
                .toList();
        return new UploadSessionEventDTO(session.sessionId(), UploadSessionStatus.CONSUMED, jobId, images);
    }

    private void scheduleCleanup(UploadSession session) {
        try {
            List<UploadSessionCleanupMessage.ObjectRef> refs = session.files().stream()
                    .map(file -> new UploadSessionCleanupMessage.ObjectRef(file.bucket(), file.objectKey()))
                    .toList();
            eventPublisher.scheduleCleanup(new UploadSessionCleanupMessage(session.sessionId(), refs));
        } catch (RuntimeException ex) {
            log.warn("Unable to schedule upload-session object cleanup sessionId={}: {}",
                    session.sessionId(), ex.getMessage());
        }
    }

    private void handleCapabilityMutation(MutationResult result) {
        switch (result) {
            case SUCCESS -> {
            }
            case NOT_FOUND -> throw new UploadSessionGoneException("Upload session expired");
            case INVALID_TOKEN -> throw new UploadSessionUnauthorizedException("Invalid upload token");
            case INVALID_STATUS -> throw new UploadSessionGoneException("Upload session is no longer available");
            case LIMIT_REACHED -> throw new BusinessException(
                    "Upload session accepts at most " + properties.getMaxFiles() + " images");
            case ALREADY_PROCESSING -> throw new UploadSessionGoneException("Upload session is being consumed");
        }
    }

    private long remainingSeconds(UploadSession session) {
        long remaining = Duration.between(Instant.now(), session.expiresAt()).toSeconds();
        if (remaining <= 0) {
            throw new UploadSessionGoneException("Upload session expired");
        }
        return Math.min(remaining, properties.getTtl().toSeconds());
    }

    private String normalizeContentType(String value) {
        if (value == null) return "";
        int semicolon = value.indexOf(';');
        String normalized = semicolon >= 0 ? value.substring(0, semicolon) : value;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeFilename(String originalName) {
        String candidate = originalName == null ? "image" : originalName;
        candidate = candidate.replace('\\', '/');
        int slash = candidate.lastIndexOf('/');
        if (slash >= 0) candidate = candidate.substring(slash + 1);
        candidate = candidate.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (candidate.isBlank()) candidate = "image";
        return candidate.length() > 120 ? candidate.substring(candidate.length() - 120) : candidate;
    }

    private String generateToken() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String hashToken(String rawToken) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private boolean tokenMatches(String rawToken, String expectedHash) {
        if (!StringUtils.hasText(rawToken) || !StringUtils.hasText(expectedHash)) {
            return false;
        }
        byte[] actual = hashToken(rawToken).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    private String stripTrailingSlash(String value) {
        if (value == null) return "";
        return value.replaceAll("/+$", "");
    }
}
