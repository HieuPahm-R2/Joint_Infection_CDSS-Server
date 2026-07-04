package com.vietnam.pji.services.auth.impl;

import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.UserRepository;
import com.vietnam.pji.services.auth.PasswordRecoveryService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String OTP_KEY_PREFIX = "password_recovery:otp:";
    private static final String ATTEMPTS_KEY_PREFIX = "password_recovery:attempts:";
    private static final String COOLDOWN_KEY_PREFIX = "password_recovery:cooldown:";

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.password-recovery.otp-ttl-seconds:300}")
    private long otpTtlSeconds;

    @Value("${app.password-recovery.max-attempts:5}")
    private long maxAttempts;

    @Value("${app.password-recovery.request-cooldown-seconds:60}")
    private long requestCooldownSeconds;

    @Value("${app.password-recovery.mail-from:no-reply@pji.local}")
    private String mailFrom;

    @Override
    public void requestOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            log.info("Password recovery OTP requested for unknown email: {}", normalizedEmail);
            return;
        }
        if (!claimCooldown(normalizedEmail)) {
            log.info("Password recovery OTP request suppressed by cooldown for email: {}", normalizedEmail);
            return;
        }

        String otp = generateOtp();
        redisTemplate.opsForValue().set(otpKey(normalizedEmail), passwordEncoder.encode(otp), otpTtlSeconds,
                TimeUnit.SECONDS);
        redisTemplate.delete(attemptsKey(normalizedEmail));
        sendOtpEmail(normalizedEmail, otp);
    }

    @Override
    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            throw new InvalidDataException("Mã OTP không hợp lệ hoặc đã hết hạn.");
        }

        String otpKey = otpKey(normalizedEmail);
        String storedOtpHash = redisTemplate.opsForValue().get(otpKey);
        if (storedOtpHash == null || storedOtpHash.isBlank()) {
            throw new InvalidDataException("Mã OTP không hợp lệ hoặc đã hết hạn.");
        }

        String attemptsKey = attemptsKey(normalizedEmail);
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (Objects.equals(attempts, 1L)) {
            redisTemplate.expire(attemptsKey, otpTtlSeconds, TimeUnit.SECONDS);
        }
        if (attempts != null && attempts > maxAttempts) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);
            throw new InvalidDataException("Bạn đã nhập sai OTP quá số lần cho phép. Vui lòng yêu cầu mã mới.");
        }

        if (!passwordEncoder.matches(otp, storedOtpHash)) {
            throw new InvalidDataException("Mã OTP không hợp lệ hoặc đã hết hạn.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setRefreshToken(null);
        userRepository.save(user);
        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);
        redisTemplate.delete("refresh_token:" + normalizedEmail);
    }

    private void sendOtpEmail(String email, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("Mã OTP đặt lại mật khẩu cho tài khoản trên hệ thống 108 PJIOrthoGen");
            helper.setText(
                    """
                            <p>Xin chào,</p>
                            <p>Mã OTP đặt lại mật khẩu của bạn là: <strong>%s</strong></p>
                            <p>Mã có hiệu lực trong %d phút. Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>
                            """
                            .formatted(otp, Math.max(1, otpTtlSeconds / 60)),
                    true);
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            log.error("Unable to send password recovery OTP email to {}", email, ex);
            throw new InvalidDataException("Không thể gửi email OTP. Vui lòng thử lại sau.");
        }
    }

    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String otpKey(String email) {
        return OTP_KEY_PREFIX + email;
    }

    private String attemptsKey(String email) {
        return ATTEMPTS_KEY_PREFIX + email;
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
