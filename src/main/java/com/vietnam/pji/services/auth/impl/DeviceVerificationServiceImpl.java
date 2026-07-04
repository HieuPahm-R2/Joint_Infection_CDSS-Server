package com.vietnam.pji.services.auth.impl;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.exception.ResourceNotFoundException;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.UserRepository;
import com.vietnam.pji.services.auth.DeviceVerificationService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceVerificationServiceImpl implements DeviceVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String OTP_KEY_PREFIX = "auth:device_otp:";
    private static final String ATTEMPTS_KEY_PREFIX = "auth:device_otp_attempts:";
    private static final String CHALLENGE_KEY_PREFIX = "auth:device_challenge:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:device_otp_cooldown:";

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    @Value("${app.device-verification.otp-ttl-seconds:300}")
    private long otpTtlSeconds;

    @Value("${app.device-verification.max-attempts:5}")
    private long maxAttempts;

    @Value("${app.device-verification.request-cooldown-seconds:60}")
    private long requestCooldownSeconds;

    @Value("${app.device-verification.trusted-device-days:30}")
    private int trustedDeviceDays;

    @Value("${app.device-verification.mail-from:no-reply@pji.local}")
    private String mailFrom;

    @Override
    public String startChallenge(String email, String candidateDeviceId, String userAgent, String ipAddress) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            // Same defensive shape as PasswordRecoveryServiceImpl — we never
            // reveal whether the email exists. The caller will still issue a
            // challengeId, but no OTP key is ever written, so verify will
            // always fail.
            log.info("Device verification challenge requested for unknown email: {}", normalizedEmail);
            return UUID.randomUUID().toString();
        }
        if (!claimCooldown(normalizedEmail)) {
            throw new InvalidDataException("Vui lòng chờ trước khi yêu cầu mã OTP mới.");
        }

        String challengeId = UUID.randomUUID().toString();
        String otp = generateOtp();
        String otpKey = otpKey(normalizedEmail, challengeId);
        redisTemplate.opsForValue().set(otpKey, passwordEncoder.encode(otp), otpTtlSeconds, TimeUnit.SECONDS);

        Challenge challenge = new Challenge(normalizedEmail, candidateDeviceId, userAgent, ipAddress);
        try {
            redisTemplate.opsForValue().set(challengeKey(challengeId), objectMapper.writeValueAsString(challenge),
                    otpTtlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(otpKey);
            throw new InvalidDataException("Không thể khởi tạo phiên xác thực thiết bị.", ex);
        }

        sendOtpEmail(normalizedEmail, otp);
        return challengeId;
    }

    @Override
    public Challenge verifyChallenge(String email, String challengeId, String otp) {
        String normalizedEmail = normalizeEmail(email);
        String challengeKey = challengeKey(challengeId);
        String challengeJson = redisTemplate.opsForValue().get(challengeKey);
        if (challengeJson == null || challengeJson.isBlank()) {
            throw new InvalidDataException("Mã OTP không hợp lệ hoặc đã hết hạn.");
        }

        Challenge challenge;
        try {
            challenge = objectMapper.readValue(challengeJson, Challenge.class);
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(challengeKey);
            throw new InvalidDataException("Mã OTP không hợp lệ hoặc đã hết hạn.", ex);
        }

        if (!Objects.equals(challenge.email(), normalizedEmail)) {
            throw new InvalidDataException("Mã OTP không hợp lệ hoặc đã hết hạn.");
        }

        String otpKey = otpKey(normalizedEmail, challengeId);
        String storedOtpHash = redisTemplate.opsForValue().get(otpKey);
        if (storedOtpHash == null || storedOtpHash.isBlank()) {
            throw new InvalidDataException("Mã OTP không hợp lệ hoặc đã hết hạn.");
        }

        String attemptsKey = attemptsKey(normalizedEmail, challengeId);
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (Objects.equals(attempts, 1L)) {
            redisTemplate.expire(attemptsKey, otpTtlSeconds, TimeUnit.SECONDS);
        }
        if (attempts != null && attempts > maxAttempts) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);
            redisTemplate.delete(challengeKey);
            throw new InvalidDataException(
                    "Bạn đã nhập sai OTP quá số lần cho phép. Vui lòng đăng nhập lại để nhận mã mới.");
        }

        if (!passwordEncoder.matches(otp, storedOtpHash)) {
            throw new InvalidDataException("Mã OTP không hợp lệ hoặc đã hết hạn.");
        }

        // Defensive: ensure the user still exists at verify time.
        if (userRepository.findByEmail(normalizedEmail) == null) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);
            redisTemplate.delete(challengeKey);
            throw new ResourceNotFoundException("Tài khoản không tồn tại.");
        }

        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);
        redisTemplate.delete(challengeKey);
        return challenge;
    }

    @Override
    public int trustedDeviceDays() {
        return trustedDeviceDays;
    }

    private void sendOtpEmail(String email, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("Cảnh báo đăng nhập từ thiết bị mới — 108 PJIOrthoGen");
            helper.setText(
                    """
                            <p>Xin chào,</p>
                            <p>Hệ thống ghi nhận một lần đăng nhập từ thiết bị chưa được xác thực vào tài khoản của bạn.</p>
                            <p>Mã OTP xác thực thiết bị: <strong>%s</strong></p>
                            <p>Mã có hiệu lực trong %d phút. Sau khi xác thực thành công, thiết bị mới sẽ thay thế thiết bị đang đăng nhập hiện tại.</p>
                            <p>Nếu bạn không thực hiện đăng nhập này, vui lòng bỏ qua email và đổi mật khẩu ngay.</p>
                            """
                            .formatted(otp, Math.max(1, otpTtlSeconds / 60)),
                    true);
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            log.error("Unable to send device-verification OTP email to {}", email, ex);
            throw new InvalidDataException("Không thể gửi email OTP xác thực thiết bị. Vui lòng thử lại sau.");
        }
    }

    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String otpKey(String email, String challengeId) {
        return OTP_KEY_PREFIX + email + ":" + challengeId;
    }

    private String attemptsKey(String email, String challengeId) {
        return ATTEMPTS_KEY_PREFIX + email + ":" + challengeId;
    }

    private String challengeKey(String challengeId) {
        return CHALLENGE_KEY_PREFIX + challengeId;
    }

    private boolean claimCooldown(String email) {
        if (requestCooldownSeconds <= 0) {
            return true;
        }
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
                COOLDOWN_KEY_PREFIX + email,
                "1",
                requestCooldownSeconds,
                TimeUnit.SECONDS);
        return Boolean.TRUE.equals(claimed);
    }
}
