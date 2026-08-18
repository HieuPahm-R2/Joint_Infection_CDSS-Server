package com.vietnam.pji.services.feat;

import com.vietnam.pji.constant.NotificationSeverity;
import com.vietnam.pji.constant.NotificationType;
import com.vietnam.pji.dto.response.NotificationResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;

public interface NotificationService {

    /**
     * Create a notification for the given user and push it to any active SSE
     * stream for that user. Safe to call from message-consumer threads.
     */
    NotificationResponseDTO create(Long userId,
            NotificationType type,
            NotificationSeverity severity,
            String title,
            String message,
            String referenceId,
            String linkUrl);

    Page<NotificationResponseDTO> list(Long userId, boolean unreadOnly, Pageable pageable);

    long unreadCount(Long userId);

    boolean markRead(Long userId, Long notificationId);

    int markAllRead(Long userId);

    int delete(Long userId, Collection<Long> notificationIds);
}
