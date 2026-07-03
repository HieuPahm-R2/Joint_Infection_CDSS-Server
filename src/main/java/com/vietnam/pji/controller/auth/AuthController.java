package com.vietnam.pji.controller.auth;

import com.vietnam.pji.dto.request.ChangePasswordRequestDTO;
import com.vietnam.pji.dto.request.ForgotPasswordRequestDTO;
import com.vietnam.pji.dto.request.LoginDTO;
import com.vietnam.pji.dto.request.ResetPasswordRequestDTO;
import com.vietnam.pji.dto.request.UpdateOwnProfileRequestDTO;
import com.vietnam.pji.dto.request.VerifyDeviceRequestDTO;
import com.vietnam.pji.dto.response.ResLoginDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.model.auth.UserTrustedDevice;
import com.vietnam.pji.repository.UserTrustedDeviceRepository;
import com.vietnam.pji.services.auth.DeviceVerificationService;
import com.vietnam.pji.services.auth.PasswordRecoveryService;
import com.vietnam.pji.services.auth.UserService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.utils.SecurityUtils;
import com.vietnam.pji.utils.mapper.RoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}")
@Tag(name = "Authentication", description = "Login, logout, token refresh, and password recovery")
public class AuthController {

    public static final String DEVICE_ID_COOKIE = "device-id";
    public static final String REFRESH_TOKEN_COOKIE = "refresh-token";

    @Value("${secure.jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenExpire;

    @Value("${secure.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${secure.cookie.same-site:Lax}")
    private String sameSite;

    private final UserService userService;
    private final RedisService RedisService;
    private final AuthenticationManager authenticationManager;
    private final SecurityUtils securityUtils;
    private final PasswordRecoveryService passwordRecoveryService;
    private final RoleMapper roleMapper;
    private final DeviceVerificationService deviceVerificationService;
    private final UserTrustedDeviceRepository userTrustedDeviceRepository;

    @Operation(summary = "Login", description = "Authenticate by username/password. If the request comes from a previously-verified device "
            + "(device-id cookie matches a non-expired trusted-devices row) tokens are issued immediately. "
            + "Otherwise the response carries requiresDeviceVerification=true plus a challengeId, no tokens "
            + "are issued, and an OTP is emailed — the client must then POST /auth/verify-device.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated — tokens issued (trusted device) or a device-verification challenge is returned (new device)"),
            @ApiResponse(responseCode = "404", description = "User not found after authentication")
    })
    @PostMapping("/auth/login")
    public ResponseEntity<ResponseData<ResLoginDTO>> login(
            @Valid @RequestBody LoginDTO loginData,
            @CookieValue(name = DEVICE_ID_COOKIE, required = false) String deviceIdCookie,
            HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                loginData.getUsername(), loginData.getPassword());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User realUser = this.userService.handleGetUserByUsername(loginData.getUsername());
        if (realUser == null) {
            throw new ResourceNotFoundException("User not found after authentication");
        }

        boolean trustedDevice = false;
        if (deviceIdCookie != null && !deviceIdCookie.isBlank()) {
            trustedDevice = userTrustedDeviceRepository
                    .findByUserIdAndDeviceIdAndExpiresAtAfter(realUser.getId(), deviceIdCookie, Instant.now())
                    .isPresent();
        }

        if (!trustedDevice) {
            // New device → don't issue tokens. Start an OTP challenge and ask
            // the client to call /auth/verify-device with the code.
            String candidateDeviceId = UUID.randomUUID().toString();
            String challengeId = deviceVerificationService.startChallenge(
                    realUser.getEmail(), candidateDeviceId, userAgent(request), clientIp(request));

            ResLoginDTO pending = new ResLoginDTO();
            pending.setRequiresDeviceVerification(true);
            pending.setChallengeId(challengeId);
            pending.setMaskedEmail(maskEmail(realUser.getEmail()));
            // Stash the candidate device id in the challenge — we don't trust
            // the client to echo it back. Hold it for the upsert in verify-device.
            return ResponseEntity.ok()
                    .body(new ResponseData<>(HttpStatus.OK.value(),
                            "Cần xác thực thiết bị bằng OTP đã gửi qua email.", pending));
        }

        // Trusted device path → mint tokens with a fresh session id; this
        // overwrites any previous active session so a parallel login on
        // another trusted device will still kick the older session out.
        ResLoginDTO resLoginDTO = buildLoginPayload(realUser);
        String sessionId = UUID.randomUUID().toString();
        issueTokensAndPersistSession(realUser, resLoginDTO, sessionId);
        String refreshToken = realUser.getRefreshToken();

        // Sliding 30-day window on the trusted device row.
        userTrustedDeviceRepository.findByUserIdAndDeviceId(realUser.getId(), deviceIdCookie)
                .ifPresent(device -> {
                    device.setLastSeenAt(Instant.now());
                    device.setExpiresAt(
                            Instant.now().plus(deviceVerificationService.trustedDeviceDays(), ChronoUnit.DAYS));
                    device.setIpAddress(clientIp(request));
                    device.setDeviceLabel(userAgent(request));
                    userTrustedDeviceRepository.save(device);
                });

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(refreshToken).toString())
                .header(HttpHeaders.SET_COOKIE, deviceIdCookie(deviceIdCookie).toString())
                .body(new ResponseData<>(HttpStatus.OK.value(), "Login successful", resLoginDTO));
    }

    @Operation(summary = "Verify new device", description = "Confirms the email OTP issued during a new-device login attempt. On success the candidate "
            + "device is added to the user's trusted-devices list, the previous active session is revoked, "
            + "fresh tokens are issued, and both the refresh-token and device-id cookies are set.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP verified — device trusted, previous session revoked, fresh tokens issued"),
            @ApiResponse(responseCode = "404", description = "Account does not exist")
    })
    @PostMapping("/auth/verify-device")
    public ResponseEntity<ResponseData<ResLoginDTO>> verifyDevice(
            @Valid @RequestBody VerifyDeviceRequestDTO data,
            HttpServletRequest request) {
        DeviceVerificationService.Challenge challenge = deviceVerificationService.verifyChallenge(
                data.getEmail(), data.getChallengeId(), data.getOtp());

        User user = userService.handleGetUserByUsername(challenge.email());
        if (user == null) {
            throw new ResourceNotFoundException("Tài khoản không tồn tại.");
        }

        // Upsert trusted device record with sliding 30-day window.
        Instant now = Instant.now();
        Instant expiresAt = now.plus(deviceVerificationService.trustedDeviceDays(), ChronoUnit.DAYS);
        UserTrustedDevice device = userTrustedDeviceRepository
                .findByUserIdAndDeviceId(user.getId(), challenge.candidateDeviceId())
                .orElseGet(() -> UserTrustedDevice.builder()
                        .userId(user.getId())
                        .deviceId(challenge.candidateDeviceId())
                        .build());
        device.setDeviceLabel(challenge.userAgent());
        device.setIpAddress(challenge.ipAddress());
        device.setLastSeenAt(now);
        device.setExpiresAt(expiresAt);
        userTrustedDeviceRepository.save(device);

        ResLoginDTO resLoginDTO = buildLoginPayload(user);
        String sessionId = UUID.randomUUID().toString();
        issueTokensAndPersistSession(user, resLoginDTO, sessionId);
        String refreshToken = user.getRefreshToken();

        long deviceCookieMaxAge = (long) deviceVerificationService.trustedDeviceDays() * 24L * 60L * 60L;

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(refreshToken).toString())
                .header(HttpHeaders.SET_COOKIE,
                        deviceCookie(challenge.candidateDeviceId(), deviceCookieMaxAge).toString())
                .body(new ResponseData<>(HttpStatus.OK.value(), "Xác thực thiết bị thành công", resLoginDTO));
    }

    @Operation(summary = "Request password reset OTP", description = "Sends a one-time code to the user's email if it exists")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "If the email exists, an OTP is emailed. The response is identical whether or not the email is registered (to avoid account enumeration)."))
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<ResponseData<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO data) {
        passwordRecoveryService.requestOtp(data.getEmail());
        return ResponseEntity.ok(new ResponseData<>(
                HttpStatus.OK.value(),
                "Nếu email tồn tại trong hệ thống, mã OTP đã được gửi."));
    }

    @Operation(summary = "Reset password", description = "Confirms OTP and sets a new password for the account")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Password reset successfully"))
    @PostMapping("/auth/reset-password")
    public ResponseEntity<ResponseData<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO data) {
        passwordRecoveryService.resetPassword(data.getEmail(), data.getOtp(), data.getNewPassword());
        return ResponseEntity.ok(new ResponseData<>(HttpStatus.OK.value(), "Đặt lại mật khẩu thành công."));
    }

    @Operation(summary = "Get current account", description = "Returns the authenticated user's profile and role")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current account profile and role"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @GetMapping("/auth/account")
    public ResponseData<ResLoginDTO.GetAccountUser> getAccount() {
        String emailLogin = SecurityUtils.getCurrentUserLogin().isPresent()
                ? SecurityUtils.getCurrentUserLogin().get()
                : "";
        User userCreated = this.userService.handleGetUserByUsername(emailLogin);
        ResLoginDTO.UserData userData = new ResLoginDTO.UserData();
        ResLoginDTO.GetAccountUser info = new ResLoginDTO.GetAccountUser();
        if (userCreated != null) {
            userData.setId(userCreated.getId());
            userData.setEmail(userCreated.getEmail());
            userData.setName(userCreated.getFullName());
            userData.setRole(roleMapper.toDetail(userCreated.getRole()));
            userData.setPhone(userCreated.getPhone());
            userData.setDepartment(userCreated.getDepartment());
            userData.setAvatar(userCreated.getAvatar());
            info.setUser(userData);
        }
        return new ResponseData<>(HttpStatus.OK.value(), "Fetch account successfully", info);
    }

    @Operation(summary = "Update own profile", description = "Self-service update of the authenticated user's name, phone, department, and avatar. "
            + "Role, status, email, and password are intentionally not editable through this endpoint — "
            + "password changes go through POST /auth/change-password.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PutMapping("/auth/account")
    public ResponseData<ResLoginDTO.UserData> updateOwnProfile(@Valid @RequestBody UpdateOwnProfileRequestDTO data) {
        String emailLogin = SecurityUtils.getCurrentUserLogin().orElseThrow(
                () -> new InvalidDataException("Phiên đăng nhập không hợp lệ."));
        User updated = userService.updateOwnProfile(emailLogin, data);
        ResLoginDTO.UserData payload = new ResLoginDTO.UserData(
                updated.getId(),
                updated.getFullName(),
                updated.getEmail(),
                roleMapper.toDetail(updated.getRole()));
        payload.setPhone(updated.getPhone());
        payload.setDepartment(updated.getDepartment());
        payload.setAvatar(updated.getAvatar());
        return new ResponseData<>(HttpStatus.OK.value(), "Cập nhật thông tin tài khoản thành công", payload);
    }

    @Operation(summary = "Change own password", description = "Self-service password change. Requires the current password; "
            + "on success every session is revoked (refresh token + active session) and the user must log in again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed — every session is revoked and the user must log in again"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PostMapping("/auth/change-password")
    public ResponseEntity<ResponseData<Void>> changeOwnPassword(@Valid @RequestBody ChangePasswordRequestDTO data) {
        String emailLogin = SecurityUtils.getCurrentUserLogin().orElseThrow(
                () -> new InvalidDataException("Phiên đăng nhập không hợp lệ."));
        userService.changeOwnPassword(emailLogin, data);
        this.RedisService.deleteActiveSession(emailLogin);

        // Xoá cookie refresh-token như logout — client phải đăng nhập lại.
        ResponseCookie removeRefresh = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, removeRefresh.toString())
                .body(new ResponseData<>(HttpStatus.OK.value(),
                        "Đổi mật khẩu thành công. Vui lòng đăng nhập lại."));
    }

    @Operation(summary = "Refresh access token", description = "Exchanges the refresh-token cookie for a new access token and rotates the refresh cookie")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New access token issued and refresh cookie rotated"),
            @ApiResponse(responseCode = "400", description = "Refresh token missing, invalid, or expired"),
            @ApiResponse(responseCode = "401", description = "Session revoked by a newer login on another device")
    })
    @GetMapping("/auth/refresh")
    public ResponseEntity<ResponseData<ResLoginDTO>> getRefreshToken(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, defaultValue = "error") String refreshToken) throws Exception {
        if (refreshToken.equals("error")) {
            throw new ResourceNotFoundException("Refresh token not be attached in request");
        }
        Jwt correctToken;
        try {
            correctToken = this.securityUtils.confirmValidRefreshToken(refreshToken);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseData<>(HttpStatus.BAD_REQUEST.value(), "Invalid or expired refresh token"));
        }
        String email = correctToken.getSubject();
        String tokenSid = correctToken.getClaimAsString("sid");

        if (!this.RedisService.validateRefreshToken(email, refreshToken)) {
            User currentUser = this.userService.fetchWithTokenAndEmail(refreshToken, email);
            if (currentUser == null) {
                throw new Exception("Invalid refresh token");
            }
        }

        // Reject refreshes from a device whose session was revoked by a newer
        // login. Without this the kicked device could quietly mint a fresh
        // access token from its still-valid refresh token.
        String activeSid = this.RedisService.getActiveSession(email);
        if (activeSid != null && tokenSid != null && !activeSid.equals(tokenSid)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ResponseData<>(HttpStatus.UNAUTHORIZED.value(),
                            "Phiên đã bị thu hồi do đăng nhập trên thiết bị khác."));
        }

        User realUser = this.userService.handleGetUserByUsername(email);
        if (realUser == null) {
            throw new ResourceNotFoundException("User not found");
        }
        ResLoginDTO resLoginDTO = buildLoginPayload(realUser);
        // Preserve sid across refresh so the still-active session keeps its id.
        String sessionId = tokenSid != null && !tokenSid.isBlank() ? tokenSid : UUID.randomUUID().toString();
        issueTokensAndPersistSession(realUser, resLoginDTO, sessionId);
        String rotatedRefresh = realUser.getRefreshToken();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(rotatedRefresh).toString())
                .body(new ResponseData<>(HttpStatus.OK.value(), "Token refreshed successfully", resLoginDTO));
    }

    @Operation(summary = "Logout", description = "Invalidates the current refresh token and clears the cookie")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged out — refresh token invalidated and cookie cleared"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid access token")
    })
    @PostMapping("/auth/logout")
    public ResponseEntity<ResponseData<Void>> logoutAccount() {
        String email = SecurityUtils.getCurrentUserLogin().isPresent()
                ? SecurityUtils.getCurrentUserLogin().get()
                : "";
        if (email.isEmpty()) {
            throw new InvalidDataException("Something wrong with access token");
        }
        this.RedisService.deleteRefreshToken(email);
        this.RedisService.deleteActiveSession(email);
        this.userService.saveRefreshToken(null, email);

        ResponseCookie removeRefresh = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();
        // Keep the device-id cookie set so the user can re-log on this same
        // browser without going through OTP again (their trusted_devices row
        // still exists).
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, removeRefresh.toString())
                .body(new ResponseData<>(HttpStatus.OK.value(), "Logout successful"));
    }

    // ===== helpers =====

    private ResLoginDTO buildLoginPayload(User realUser) {
        ResLoginDTO resLoginDTO = new ResLoginDTO();
        ResLoginDTO.UserData userLog = new ResLoginDTO.UserData(
                realUser.getId(),
                realUser.getFullName(),
                realUser.getEmail(),
                roleMapper.toDetail(realUser.getRole()));
        resLoginDTO.setUser(userLog);
        return resLoginDTO;
    }

    /**
     * Mint access + refresh tokens with the given sid, persist active session in
     * Redis + DB.
     */
    private void issueTokensAndPersistSession(User user, ResLoginDTO resLoginDTO, String sessionId) {
        String accessToken = securityUtils.generateAccessToken(user.getEmail(), resLoginDTO, sessionId);
        resLoginDTO.setAccessToken(accessToken);
        String refreshToken = securityUtils.generateRefreshToken(user.getEmail(), resLoginDTO, sessionId);
        RedisService.saveRefreshToken(user.getEmail(), refreshToken, refreshTokenExpire);
        RedisService.saveActiveSession(user.getEmail(), sessionId, refreshTokenExpire);
        userService.saveRefreshToken(refreshToken, user.getEmail());
        userService.updateLastLogin(user.getEmail());
        user.setRefreshToken(refreshToken);
    }

    private ResponseCookie refreshTokenCookie(String value) {
        return ResponseCookie
                .from(REFRESH_TOKEN_COOKIE, value == null ? "" : value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(refreshTokenExpire)
                .build();
    }

    private ResponseCookie deviceCookie(String value, long maxAgeSeconds) {
        return ResponseCookie
                .from(DEVICE_ID_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    /**
     * Re-issue the same device-id cookie value with a fresh 30-day expiry on
     * trusted-device login.
     */
    private ResponseCookie deviceIdCookie(String value) {
        return deviceCookie(value, (long) deviceVerificationService.trustedDeviceDays() * 24L * 60L * 60L);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return null;
        }
        return ua.length() > 240 ? ua.substring(0, 240) : ua;
    }

    private String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }
}
