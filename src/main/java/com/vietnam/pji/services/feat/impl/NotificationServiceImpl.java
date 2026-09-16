package com.vietnam.pji.services.feat.impl;

import com.vietnam.pji.constant.NotificationSeverity;
import com.vietnam.pji.constant.NotificationType;
import com.vietnam.pji.controller.notification.NotificationStreamController;
import com.vietnam.pji.dto.response.NotificationResponseDTO;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.model.notification.Notification;
import com.vietnam.pji.repository.NotificationRepository;
import com.vietnam.pji.services.feat.NotificationService;
import com.vietnam.pji.utils.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collection;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationStreamController streamController;

    @Override
    @Transactional
    public NotificationResponseDTO create(Long userId,
            NotificationType type,
            NotificationSeverity severity,
            String title,
            String message,
            String referenceId,
            String linkUrl) {
        if (userId == null) {
            log.warn("Refusing to create notification with null userId (type={}, ref={})",
                    type, referenceId);
            return null;
        }

        User userRef = new User();
        userRef.setId(userId);

        Notification entity = Notification.builder()
                .user(userRef)
                .type(type)
                .severity(severity != null ? severity : NotificationSeverity.INFO)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .linkUrl(linkUrl)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(entity);
        NotificationResponseDTO dto = notificationMapper.toResponse(saved);
        pushAfterCommit(userId, dto);
        return dto;
    }

    private void pushAfterCommit(Long userId, NotificationResponseDTO dto) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            streamController.push(userId, dto);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                streamController.push(userId, dto);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponseDTO> list(Long userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(notificationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        if (userId == null)
            return 0L;
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public boolean markRead(Long userId, Long notificationId) {
        if (userId == null || notificationId == null)
            return false;
        int updated = notificationRepository.markRead(notificationId, userId, Instant.now());
        return updated > 0;
    }

    @Override
    @Transactional
    public int markAllRead(Long userId) {
        if (userId == null)
            return 0;
        return notificationRepository.markAllRead(userId, Instant.now());
    }

    @Override
    @Transactional
    public int delete(Long userId, Collection<Long> notificationIds) {
        if (userId == null || notificationIds == null || notificationIds.isEmpty()) {
            return 0;
        }
        return notificationRepository.deleteByIdsAndUserId(notificationIds, userId);
    }
}
