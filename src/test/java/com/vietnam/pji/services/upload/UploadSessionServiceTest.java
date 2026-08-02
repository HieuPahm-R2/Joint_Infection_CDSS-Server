package com.vietnam.pji.services.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vietnam.pji.config.properties.MinioProperties;
import com.vietnam.pji.config.properties.UploadSessionProperties;
import com.vietnam.pji.constant.UploadSessionStatus;
import com.vietnam.pji.exception.BusinessException;
import com.vietnam.pji.exception.UploadSessionGoneException;
import com.vietnam.pji.exception.UploadSessionUnauthorizedException;
import com.vietnam.pji.model.medical.Patient;
import com.vietnam.pji.model.medical.PjiEpisode;
import com.vietnam.pji.model.upload.UploadSession;
import com.vietnam.pji.model.upload.UploadSessionFile;
import com.vietnam.pji.repository.EpisodeRepository;
import com.vietnam.pji.repository.upload.UploadSessionRepository;
import com.vietnam.pji.services.medical.PatientService;
import com.vietnam.pji.utils.MinioChannel;

class UploadSessionServiceTest {

    private static final UUID SESSION_ID = UUID.fromString("b4ecb937-71b3-49a6-8d95-5bc6bf1fd773");
    private static final String TOKEN = "secure-mobile-capability";

    private FakeUploadSessionRepository repository;
    private UploadSessionProperties properties;
    private UploadSessionService service;

    @BeforeEach
    void setUp() {
        repository = new FakeUploadSessionRepository();
        properties = new UploadSessionProperties();
        service = serviceWith(null, null);
    }

    @Test
    void validateRejectsExpiredRedisSession() {
        repository.current = Optional.empty();

        assertThatThrownBy(() -> service.validate(SESSION_ID, TOKEN))
                .isInstanceOf(UploadSessionGoneException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateRejectsReusedTokenAfterSessionWasConsumed() {
        repository.current = Optional.of(session(UploadSessionStatus.CONSUMED));

        assertThatThrownBy(() -> service.validate(SESSION_ID, TOKEN))
                .isInstanceOf(UploadSessionGoneException.class)
                .hasMessageContaining("no longer");
    }

    @Test
    void validateRejectsWrongCapabilityToken() {
        repository.current = Optional.of(session(UploadSessionStatus.PENDING));

        assertThatThrownBy(() -> service.validate(SESSION_ID, "wrong-token"))
                .isInstanceOf(UploadSessionUnauthorizedException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void createRejectsEpisodeFromAnotherPatient() {
        Patient episodePatient = new Patient();
        episodePatient.setId(8L);
        PjiEpisode episode = new PjiEpisode();
        episode.setPatient(episodePatient);
        service = serviceWith(patientService(), episodeRepository(episode));

        assertThatThrownBy(() -> service.create(7L, 23L, 99L, "https://example.test"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong");
        assertThat(repository.created).isFalse();
    }

    @Test
    void presignRejectsUnsupportedContentTypeBeforeCallingMinio() {
        repository.current = Optional.of(session(UploadSessionStatus.PENDING));

        assertThatThrownBy(() -> service.presign(
                SESSION_ID,
                TOKEN,
                "payload.svg",
                "image/svg+xml",
                1024))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void presignRejectsOversizedImageBeforeCallingMinio() {
        repository.current = Optional.of(session(UploadSessionStatus.PENDING));

        assertThatThrownBy(() -> service.presign(
                SESSION_ID,
                TOKEN,
                "large.heic",
                "image/heic",
                properties.getMaxFileSizeBytes() + 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("between");
    }

    @Test
    void completeRejectsAndRemovesObjectWhoseActualSizeDiffers() {
        UUID fileId = UUID.fromString("eecbe691-cf83-44f7-a4ae-2b86802bf6dd");
        UploadSessionFile file = UploadSessionFile.builder()
                .fileId(fileId)
                .originalName("result.jpg")
                .contentType("image/jpeg")
                .declaredSize(128)
                .bucket("clinical-upload-sessions")
                .objectKey("upload-sessions/" + SESSION_ID + "/" + fileId + "/result.jpg")
                .createdAt(Instant.now())
                .build();
        repository.current = Optional.of(session(UploadSessionStatus.PENDING, List.of(file)));
        service = new UploadSessionService(
                repository,
                properties,
                null,
                null,
                new FakeMinioChannel(new MinioChannel.StoredObjectMetadata(64, "image/jpeg")),
                null,
                null);

        assertThatThrownBy(() -> service.complete(SESSION_ID, TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("metadata does not match");
        assertThat(repository.removedFileId).isEqualTo(fileId);
    }

    private UploadSessionService serviceWith(
            PatientService patientService,
            EpisodeRepository episodeRepository) {
        return new UploadSessionService(
                repository,
                properties,
                patientService,
                episodeRepository,
                null,
                null,
                null);
    }

    private PatientService patientService() {
        return (PatientService) Proxy.newProxyInstance(
                PatientService.class.getClassLoader(),
                new Class<?>[] {PatientService.class},
                (proxy, method, args) -> {
                    if ("getById".equals(method.getName())) {
                        Patient patient = new Patient();
                        patient.setId((Long) args[0]);
                        return patient;
                    }
                    return null;
                });
    }

    private EpisodeRepository episodeRepository(PjiEpisode episode) {
        return (EpisodeRepository) Proxy.newProxyInstance(
                EpisodeRepository.class.getClassLoader(),
                new Class<?>[] {EpisodeRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) return Optional.of(episode);
                    return defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private UploadSession session(UploadSessionStatus status) {
        return session(status, List.of());
    }

    private UploadSession session(UploadSessionStatus status, List<UploadSessionFile> files) {
        return UploadSession.builder()
                .sessionId(SESSION_ID)
                .tokenHash(hash(TOKEN))
                .patientId(7L)
                .episodeId(23L)
                .doctorId(99L)
                .createdAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(240))
                .status(status)
                .files(files)
                .build();
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class FakeUploadSessionRepository implements UploadSessionRepository {
        private Optional<UploadSession> current = Optional.empty();
        private boolean created;
        private UUID removedFileId;

        @Override
        public void create(UploadSession session, Duration ttl) {
            created = true;
            current = Optional.of(session);
        }

        @Override
        public Optional<UploadSession> find(UUID sessionId) {
            return current.filter(session -> session.sessionId().equals(sessionId));
        }

        @Override
        public MutationResult addFile(
                UUID sessionId,
                String tokenHash,
                UploadSessionFile file,
                int maxFiles) {
            return MutationResult.SUCCESS;
        }

        @Override
        public MutationResult markUploaded(UUID sessionId, String tokenHash, Instant completedAt) {
            return MutationResult.SUCCESS;
        }

        @Override
        public MutationResult claimProcessing(UUID sessionId) {
            return MutationResult.SUCCESS;
        }

        @Override
        public void removeFile(UUID sessionId, UUID fileId) {
            removedFileId = fileId;
        }

        @Override
        public void releaseProcessing(UUID sessionId) {
        }

        @Override
        public void restorePending(UUID sessionId) {
        }

        @Override
        public void markConsumed(UUID sessionId, String jobId) {
        }
    }

    private static final class FakeMinioChannel extends MinioChannel {
        private final StoredObjectMetadata metadata;

        private FakeMinioChannel(StoredObjectMetadata metadata) {
            super(null, null, new MinioProperties());
            this.metadata = metadata;
        }

        @Override
        public StoredObjectMetadata statObject(String bucket, String objectKey) {
            return metadata;
        }

        @Override
        public byte[] readPrefix(String bucket, String objectKey, int length) {
            return new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        }

        @Override
        public void deleteObject(String bucket, String objectKey) {
        }
    }
}
