package com.vietnam.pji.services.feat;

public interface RedisService {
    /**
     * save refresh token vào Redis với TTL
     *
     * @param email                   email người dùng
     * @param refreshToken            token cần lưu
     * @param expirationTimeInSeconds thời gian hết hạn
     */
    void saveRefreshToken(String email, String refreshToken, long expirationTimeInSeconds);

    /**
     * Lấy refresh token từ Redis
     *
     * @param email email user
     * @return refresh token hoặc null nếu không tồn tại
     */
    String getRefreshToken(String email);

    /**
     * Xóa refresh token khỏi Redis (logout)
     *
     * @param email email người dùng
     */
    void deleteRefreshToken(String email);

    /**
     * Kiểm tra refresh token có tồn tại trong Redis
     *
     * @param email        email người dùng
     * @param refreshToken token cần kiểm tra
     * @return true nếu token hợp lệ
     */
    boolean validateRefreshToken(String email, String refreshToken);

    /**
     * Atomically replace the current refresh token when the presented token
     * matches. Returns false for replay, revocation, or an expired session.
     */
    boolean rotateRefreshToken(
            String email,
            String presentedRefreshToken,
            String replacementRefreshToken,
            long expirationTimeInSeconds);

    /**
     * Lưu id phiên đang hoạt động cho email (single-device enforcement).
     * Mọi access/refresh token chứa claim "sid" khác giá trị này sẽ bị
     * ActiveSessionFilter từ chối với mã SESSION_REVOKED.
     */
    void saveActiveSession(String email, String sessionId, long expirationTimeInSeconds);

    /** Lấy id phiên đang hoạt động cho email (null nếu chưa có). */
    String getActiveSession(String email);

    /** Xóa id phiên đang hoạt động (logout / revoke). */
    void deleteActiveSession(String email);

    /**
     * Thêm access token vào blacklist khi logout
     *
     * @param accessToken             token cần blacklist
     * @param expirationTimeInSeconds thời gian hết hạn
     */
    void blacklistAccessToken(String accessToken, long expirationTimeInSeconds);

    /**
     * Kiểm tra access token có trong blacklist
     *
     * @param accessToken token cần kiểm tra
     * @return true nếu token bị blacklist
     */
    boolean isAccessTokenBlacklisted(String accessToken);

    // ===== User Permission Caching =====

    void cacheUserPermissions(String email, String permissionsJson, long ttlSeconds);

    String getCachedUserPermissions(String email);

    void evictUserPermissions(String email);

    void evictAllUserPermissions();

    // ===== Episode Snapshot Caching =====

    void cacheSnapshot(Long episodeId, String snapshotJson, long ttlSeconds);

    String getCachedSnapshot(Long episodeId);

    void evictSnapshotCache(Long episodeId);

    // ===== AI Run Detail Caching =====

    void cacheRunDetail(Long runId, String detailJson, long ttlSeconds);

    String getCachedRunDetail(Long runId);

    void evictRunDetail(Long runId);

    // ===== AI Run Cancellation Signaling =====

    /**
     * Mark a run as cancelled so Python workers polling Redis between steps see
     * the flag and abort. TTL should be at least as long as the worst-case run
     * latency; the flag is harmless once the run row is in a terminal state.
     */
    void markRunCancelled(Long runId, long ttlSeconds);

    /** Test helper: check whether a run id currently has a cancel flag set. */
    boolean isRunCancelled(Long runId);

    /** Clear the cancel flag — primarily for tests / admin reset. */
    void clearRunCancelled(Long runId);
}
