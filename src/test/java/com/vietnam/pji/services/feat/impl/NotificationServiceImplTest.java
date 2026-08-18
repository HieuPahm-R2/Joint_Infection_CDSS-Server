package com.vietnam.pji.services.feat.impl;

import com.vietnam.pji.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
