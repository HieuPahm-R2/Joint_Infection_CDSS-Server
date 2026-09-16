package com.vietnam.pji.services.feat.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

class RedisServiceImplTest {

    @Test
    void evictsPermissionCacheWithCursorScanAndBoundedDeleteBatches() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        List<String> keys = IntStream.range(0, 501)
                .mapToObj(index -> "user_permissions:user-" + index)
                .toList();
        AtomicInteger position = new AtomicInteger();
        List<List<String>> deletedBatches = new ArrayList<>();

        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenAnswer(invocation -> position.get() < keys.size());
        when(cursor.next()).thenAnswer(invocation -> keys.get(position.getAndIncrement()));
        doAnswer(invocation -> {
            deletedBatches.add(List.copyOf(invocation.getArgument(0)));
            return null;
        }).when(redisTemplate).delete(any(java.util.Collection.class));

        new RedisServiceImpl(redisTemplate).evictAllUserPermissions();

        assertThat(deletedBatches).hasSize(2);
        assertThat(deletedBatches.get(0)).hasSize(500);
        assertThat(deletedBatches.get(1)).containsExactly("user_permissions:user-500");
        verify(redisTemplate, never()).keys(anyString());
        verify(cursor).close();
    }
}
