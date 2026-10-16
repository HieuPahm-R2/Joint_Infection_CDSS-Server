package com.vietnam.pji.controller.medical;

import com.vietnam.pji.dto.response.AdmissionLockResponseDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.exception.ForbiddenException;
import com.vietnam.pji.services.episode.AdmissionLockService;
import com.vietnam.pji.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}")
@Validated
@Tag(name = "Episode Lock", description = "Soft (pessimistic) edit lock on a PJI episode — backed by Redis with a 3-minute TTL")
@RequiredArgsConstructor
public class AdmissionLockController {

    private final AdmissionLockService episodeLockService;

    @Operation(summary = "Acquire edit lock", description = "Doctor starts editing. Creates a Redis lock with a 3-minute TTL. "
            +
            "Returns 423 LOCKED if another user already holds the lock.")
    @PostMapping("/episodes/{id}/lock")
    public ResponseData<AdmissionLockResponseDTO> acquire(@PathVariable("id") Long episodeId) {
        Long userId = currentUserId();
        return new ResponseData<>(HttpStatus.OK.value(), "Lock acquired",
                episodeLockService.acquire(episodeId, userId));
    }

    @Operation(summary = "Heartbeat / renew lock", description = "Extend the TTL while the editor is still open. " +
            "Returns 423 LOCKED if the caller is no longer the holder.")
    @PostMapping("/episodes/{id}/lock/heartbeat")
    public ResponseData<AdmissionLockResponseDTO> heartbeat(@PathVariable("id") Long episodeId) {
        Long userId = currentUserId();
        return new ResponseData<>(HttpStatus.OK.value(), "Lock renewed",
                episodeLockService.renew(episodeId, userId));
    }

    @Operation(summary = "Release lock", description = "Release the lock after the doctor saves or cancels the edit. " +
            "No-op if the lock has already expired or belongs to someone else.")
    @DeleteMapping("/episodes/{id}/lock")
    public ResponseData<Void> release(@PathVariable("id") Long episodeId) {
        Long userId = currentUserId();
        episodeLockService.release(episodeId, userId);
        return new ResponseData<>(HttpStatus.OK.value(), "Lock released");
    }

    private static Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ForbiddenException("Authenticated user required");
        }
        return userId;
    }
}
