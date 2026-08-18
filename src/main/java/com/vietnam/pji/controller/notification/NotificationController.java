package com.vietnam.pji.controller.notification;

import com.vietnam.pji.dto.request.DeleteNotificationsRequestDTO;
import com.vietnam.pji.dto.response.NotificationResponseDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.services.feat.NotificationService;
import com.vietnam.pji.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import jakarta.validation.Valid;

@RestController
@RequestMapping("${api.prefix}/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Per-user notification inbox")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List notifications for the authenticated user")
    public ResponseData<Page<NotificationResponseDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long userId = requireUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, MAX_PAGE_SIZE));
        Page<NotificationResponseDTO> result = notificationService.list(userId, unreadOnly, pageable);
        return new ResponseData<>(HttpStatus.OK.value(), "OK", result);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get the unread notification count for the authenticated user")
    public ResponseData<Map<String, Long>> unreadCount() {
        Long userId = requireUserId();
        long count = notificationService.unreadCount(userId);
        return new ResponseData<>(HttpStatus.OK.value(), "OK", Map.of("unreadCount", count));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public ResponseData<Map<String, Boolean>> markRead(@PathVariable Long id) {
        Long userId = requireUserId();
        boolean updated = notificationService.markRead(userId, id);
        return new ResponseData<>(HttpStatus.OK.value(), "OK", Map.of("updated", updated));
    }

    @PostMapping("/mark-all-read")
    @Operation(summary = "Mark every unread notification as read")
    public ResponseData<Map<String, Integer>> markAllRead() {
        Long userId = requireUserId();
        int count = notificationService.markAllRead(userId);
        return new ResponseData<>(HttpStatus.OK.value(), "OK", Map.of("updated", count));
    }

    @DeleteMapping
    @Operation(summary = "Delete one or more notifications for the authenticated user")
    public ResponseData<Map<String, Integer>> delete(
            @Valid @RequestBody DeleteNotificationsRequestDTO request) {
        Long userId = requireUserId();
        int count = notificationService.delete(userId, request.getIds());
        return new ResponseData<>(HttpStatus.OK.value(), "OK", Map.of("deleted", count));
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthenticated");
        }
        return userId;
    }
}
