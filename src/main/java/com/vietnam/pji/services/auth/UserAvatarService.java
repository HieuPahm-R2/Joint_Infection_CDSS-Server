package com.vietnam.pji.services.auth;

import com.vietnam.pji.config.properties.AvatarStorageProperties;
import com.vietnam.pji.dto.request.UpdateOwnProfileRequestDTO;
import com.vietnam.pji.exception.BusinessException;
import com.vietnam.pji.exception.PayloadTooLargeException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.exception.StorageUnavailableException;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.UserRepository;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.utils.MinioChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAvatarService {

    static final long MAX_AVATAR_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final RedisService redisService;
    private final MinioChannel minioChannel;
    private final AvatarStorageProperties avatarStorageProperties;

    @Transactional
    public User updateProfileWithAvatar(
            String email,
            UpdateOwnProfileRequestDTO data,
            MultipartFile avatar) {
        validateAvatar(avatar);

        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        applyProfile(data, user);
        String previousBucket = user.getAvatarBucket();
        String previousObjectKey = user.getAvatarObjectKey();

        MinioChannel.UploadResult uploaded = storeAvatar(avatar);
        registerObjectCleanup(uploaded, previousBucket, previousObjectKey);

        user.setAvatar(null);
        user.setAvatarBucket(uploaded.bucket());
        user.setAvatarObjectKey(uploaded.objectKey());
        User saved = userRepository.save(user);
        initializeRolePermissions(saved);
        redisService.evictUserPermissions(saved.getEmail());
        return saved;
    }

    public String resolveAvatarUrl(User user) {
        if (user == null) {
            return null;
        }
        if (hasText(user.getAvatarBucket()) && hasText(user.getAvatarObjectKey())) {
            try {
                return minioChannel.presignedGetUrl(user.getAvatarBucket(), user.getAvatarObjectKey());
            } catch (Exception ex) {
                log.warn("Unable to generate avatar URL for object {}/{}: {}",
                        user.getAvatarBucket(), user.getAvatarObjectKey(), ex.getMessage());
            }
        }
        return user.getAvatar();
    }

    private MinioChannel.UploadResult storeAvatar(MultipartFile avatar) {
        String bucket = avatarStorageProperties.getBucket();
        try {
            minioChannel.initPrivateBucket(bucket);
            return minioChannel.storeObject(avatar, bucket);
        } catch (Exception ex) {
            log.error("Avatar storage is unavailable for bucket {}", bucket, ex);
            throw new StorageUnavailableException(
                    "Dịch vụ lưu trữ ảnh đang tạm thời không khả dụng. Vui lòng thử lại sau.", ex);
        }
    }

    private void validateAvatar(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ảnh đại diện.");
        }
        if (avatar.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new PayloadTooLargeException("Ảnh đại diện không được vượt quá 5 MB.");
        }

        String contentType = avatar.getContentType() == null
                ? "" : avatar.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("Ảnh đại diện phải có định dạng JPEG, PNG hoặc WEBP.");
        }

        try {
            byte[] header = avatar.getInputStream().readNBytes(12);
            if (!matchesImageSignature(header, contentType)) {
                throw new BusinessException("Nội dung tệp không khớp với định dạng ảnh đã chọn.");
            }
        } catch (IOException ex) {
            throw new BusinessException("Không thể đọc tệp ảnh đại diện.");
        }
    }

    private boolean matchesImageSignature(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && unsigned(bytes[0]) == 0xFF
                    && unsigned(bytes[1]) == 0xD8
                    && unsigned(bytes[2]) == 0xFF;
            case "image/png" -> bytes.length >= 8
                    && unsigned(bytes[0]) == 0x89
                    && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A
                    && bytes[6] == 0x1A && bytes[7] == 0x0A;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private void applyProfile(UpdateOwnProfileRequestDTO data, User user) {
        user.setFullName(data.getFullName());
        if (data.getPhone() != null) {
            user.setPhone(data.getPhone().isBlank() ? null : data.getPhone());
        }
        if (data.getDepartment() != null) {
            user.setDepartment(data.getDepartment().isBlank() ? null : data.getDepartment());
        }
    }

    private void initializeRolePermissions(User user) {
        if (user.getRole() != null && user.getRole().getPermissions() != null) {
            user.getRole().getPermissions().size();
        }
    }

    private void registerObjectCleanup(
            MinioChannel.UploadResult uploaded,
            String previousBucket,
            String previousObjectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(previousBucket, previousObjectKey);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteQuietly(previousBucket, previousObjectKey);
                } else {
                    deleteQuietly(uploaded.bucket(), uploaded.objectKey());
                }
            }
        });
    }

    private void deleteQuietly(String bucket, String objectKey) {
        if (!hasText(bucket) || !hasText(objectKey)) {
            return;
        }
        try {
            minioChannel.deleteObject(bucket, objectKey);
        } catch (RuntimeException ex) {
            log.warn("Unable to delete replaced avatar object {}/{}", bucket, objectKey, ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
