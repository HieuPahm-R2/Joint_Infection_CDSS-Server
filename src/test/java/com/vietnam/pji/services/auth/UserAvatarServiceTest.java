package com.vietnam.pji.services.auth;

import com.vietnam.pji.config.properties.AvatarStorageProperties;
import com.vietnam.pji.dto.request.UpdateOwnProfileRequestDTO;
import com.vietnam.pji.exception.BusinessException;
import com.vietnam.pji.exception.PayloadTooLargeException;
import com.vietnam.pji.exception.StorageUnavailableException;
import com.vietnam.pji.model.auth.Permission;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.UserRepository;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.utils.MinioChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAvatarServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RedisService redisService;
    @Mock
    private MinioChannel minioChannel;
    @Spy
    private AvatarStorageProperties avatarStorageProperties = new AvatarStorageProperties();

    @InjectMocks
    private UserAvatarService service;

    @Test
    void updateProfileWithAvatarPersistsStableObjectIdentityAndDeletesPreviousAvatar() {
        User user = new User();
        user.setEmail("doctor@example.com");
        user.setAvatar("https://legacy.example/avatar.png");
        user.setAvatarBucket("old-avatars");
        user.setAvatarObjectKey("old.png");
        Role role = mock(Role.class);
        @SuppressWarnings("unchecked")
        List<Permission> permissions = mock(List.class);
        when(role.getPermissions()).thenReturn(permissions);
        user.setRole(role);
        UpdateOwnProfileRequestDTO profile = profile();
        MockMultipartFile avatar = pngFile();
        MinioChannel.UploadResult uploaded = new MinioChannel.UploadResult(
                avatarStorageProperties.getBucket(), "new.png", null);

        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
        when(minioChannel.storeObject(avatar, avatarStorageProperties.getBucket())).thenReturn(uploaded);
        when(userRepository.save(user)).thenReturn(user);

        TransactionSynchronizationManager.initSynchronization();
        try {
            User result = service.updateProfileWithAvatar(user.getEmail(), profile, avatar);

            assertEquals("Bác sĩ Nguyễn", result.getFullName());
            assertEquals("0901234567", result.getPhone());
            assertEquals("Khoa Ngoại", result.getDepartment());
            assertNull(result.getAvatar());
            assertEquals(avatarStorageProperties.getBucket(), result.getAvatarBucket());
            assertEquals("new.png", result.getAvatarObjectKey());
            verify(minioChannel).initPrivateBucket(avatarStorageProperties.getBucket());
            verify(permissions).size();
            verify(minioChannel, never()).deleteObject("old-avatars", "old.png");
            verify(redisService).evictUserPermissions(user.getEmail());

            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            verify(minioChannel).deleteObject("old-avatars", "old.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateProfileWithAvatarMapsStorageFailureToServiceUnavailable() {
        User user = new User();
        user.setEmail("doctor@example.com");
        MockMultipartFile avatar = pngFile();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
        RuntimeException minioFailure = new RuntimeException("connection refused");
        when(minioChannel.storeObject(avatar, avatarStorageProperties.getBucket()))
                .thenThrow(minioFailure);

        StorageUnavailableException error = assertThrows(StorageUnavailableException.class,
                () -> service.updateProfileWithAvatar(user.getEmail(), profile(), avatar));

        assertEquals("Dịch vụ lưu trữ ảnh đang tạm thời không khả dụng. Vui lòng thử lại sau.",
                error.getMessage());
        assertEquals(minioFailure, error.getCause());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileWithAvatarRejectsSpoofedImageBeforeStorage() {
        MockMultipartFile spoofed = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", "not-an-image".getBytes());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProfileWithAvatar("doctor@example.com", profile(), spoofed));

        assertEquals("Nội dung tệp không khớp với định dạng ảnh đã chọn.", error.getMessage());
        verify(userRepository, never()).findByEmail(any());
        verify(minioChannel, never()).uploadObject(any(), any());
    }

    @Test
    void updateProfileWithAvatarRejectsOversizedImageAsPayloadTooLarge() {
        byte[] oversized = new byte[(int) UserAvatarService.MAX_AVATAR_SIZE_BYTES + 1];
        oversized[0] = (byte) 0x89;
        oversized[1] = 0x50;
        oversized[2] = 0x4E;
        oversized[3] = 0x47;
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", oversized);

        PayloadTooLargeException error = assertThrows(PayloadTooLargeException.class,
                () -> service.updateProfileWithAvatar("doctor@example.com", profile(), avatar));

        assertEquals("Ảnh đại diện không được vượt quá 5 MB.", error.getMessage());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void resolveAvatarUrlRegeneratesPresignedUrlAndSupportsLegacyValue() {
        User storedAvatar = new User();
        storedAvatar.setAvatarBucket(avatarStorageProperties.getBucket());
        storedAvatar.setAvatarObjectKey("avatar.webp");
        when(minioChannel.presignedGetUrl(avatarStorageProperties.getBucket(), "avatar.webp"))
                .thenReturn("https://signed.example/avatar.webp");

        assertEquals("https://signed.example/avatar.webp", service.resolveAvatarUrl(storedAvatar));

        User legacyAvatar = new User();
        legacyAvatar.setAvatar("https://legacy.example/avatar.jpg");
        assertEquals(legacyAvatar.getAvatar(), service.resolveAvatarUrl(legacyAvatar));
    }

    @Test
    void resolveAvatarUrlFallsBackWithoutFailingAccountApiWhenPresignerIsDown() {
        User user = new User();
        user.setAvatar("https://legacy.example/avatar.jpg");
        user.setAvatarBucket(avatarStorageProperties.getBucket());
        user.setAvatarObjectKey("avatar.webp");
        when(minioChannel.presignedGetUrl(avatarStorageProperties.getBucket(), "avatar.webp"))
                .thenThrow(new RuntimeException("invalid public endpoint"));

        assertEquals(user.getAvatar(), service.resolveAvatarUrl(user));
    }

    private UpdateOwnProfileRequestDTO profile() {
        UpdateOwnProfileRequestDTO profile = new UpdateOwnProfileRequestDTO();
        profile.setFullName("Bác sĩ Nguyễn");
        profile.setPhone("0901234567");
        profile.setDepartment("Khoa Ngoại");
        return profile;
    }

    private MockMultipartFile pngFile() {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
        return new MockMultipartFile("avatar", "avatar.png", "image/png", png);
    }
}
