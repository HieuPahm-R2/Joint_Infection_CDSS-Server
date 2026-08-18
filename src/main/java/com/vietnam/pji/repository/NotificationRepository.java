package com.vietnam.pji.repository;

import com.vietnam.pji.model.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt "
            + "WHERE n.id = :id AND n.user.id = :userId AND n.isRead = false")
    int markRead(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("readAt") Instant readAt);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt "
            + "WHERE n.user.id = :userId AND n.isRead = false")
    int markAllRead(@Param("userId") Long userId, @Param("readAt") Instant readAt);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN :ids AND n.user.id = :userId")
    int deleteByIdsAndUserId(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);
}
