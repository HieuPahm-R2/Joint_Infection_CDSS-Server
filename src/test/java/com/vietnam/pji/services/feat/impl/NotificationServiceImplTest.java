package com.vietnam.pji.services.feat.impl;

import com.vietnam.pji.constant.NotificationSeverity;
import com.vietnam.pji.constant.NotificationType;
import com.vietnam.pji.controller.notification.NotificationStreamController;
import com.vietnam.pji.dto.response.NotificationResponseDTO;
import com.vietnam.pji.model.notification.Notification;
import com.vietnam.pji.repository.NotificationRepository;
import com.vietnam.pji.utils.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    @Test
    void deleteRestrictsTheDeletionToTheAuthenticatedUser() {
        List<Long> ids = List.of(11L, 12L);
        AtomicReference<List<Long>> receivedIds = new AtomicReference<>();
        AtomicReference<Long> receivedUserId = new AtomicReference<>();
        NotificationRepository notificationRepository = repository((method, args) -> {
            if (method.getName().equals("deleteByIdsAndUserId")) {
                receivedIds.set(((Collection<?>) args[0]).stream().map(Long.class::cast).toList());
                receivedUserId.set((Long) args[1]);
                return 2;
            }
            throw new AssertionError("Unexpected repository method: " + method.getName());
        });
        NotificationServiceImpl notificationService = new NotificationServiceImpl(notificationRepository, null, null);

        int deleted = notificationService.delete(7L, ids);

        assertThat(deleted).isEqualTo(2);
        assertThat(receivedIds.get()).containsExactlyElementsOf(ids);
        assertThat(receivedUserId.get()).isEqualTo(7L);
    }

    @Test
    void deleteIgnoresEmptyRequests() {
        AtomicBoolean repositoryCalled = new AtomicBoolean();
        NotificationRepository notificationRepository = repository((method, args) -> {
            repositoryCalled.set(true);
            throw new AssertionError("Repository must not be called");
        });
        NotificationServiceImpl notificationService = new NotificationServiceImpl(notificationRepository, null, null);

        assertThat(notificationService.delete(7L, List.of())).isZero();
        assertThat(repositoryCalled).isFalse();
    }

    @Test
    void pushesNotificationOnlyAfterTheTransactionCommits() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        NotificationStreamController streamController = mock(NotificationStreamController.class);
        NotificationServiceImpl notificationService = new NotificationServiceImpl(
                notificationRepository, notificationMapper, streamController);
        Notification notification = Notification.builder().build();
        NotificationResponseDTO response = NotificationResponseDTO.builder().id(11L).build();
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class))).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(response);

        TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.create(7L, NotificationType.AI_RECOMMENDATION_DONE,
                    NotificationSeverity.SUCCESS, "done", "message", "11", "/?runId=11");

            verify(streamController, never()).push(eq(7L), eq(response));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(streamController, never()).push(eq(7L), eq(response));

            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.initSynchronization();
            notificationService.create(7L, NotificationType.AI_RECOMMENDATION_DONE,
                    NotificationSeverity.SUCCESS, "done", "message", "11", "/?runId=11");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(streamController).push(7L, response);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private NotificationRepository repository(RepositoryInvocation invocation) {
        return (NotificationRepository) Proxy.newProxyInstance(
                NotificationRepository.class.getClassLoader(),
                new Class<?>[] { NotificationRepository.class },
                (proxy, method, args) -> invocation.invoke(method, args));
    }

    @FunctionalInterface
    private interface RepositoryInvocation {
        Object invoke(java.lang.reflect.Method method, Object[] args);
    }
}
