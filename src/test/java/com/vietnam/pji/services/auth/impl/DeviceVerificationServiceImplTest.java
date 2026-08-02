package com.vietnam.pji.services.auth.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.UserRepository;
import com.vietnam.pji.services.auth.DeviceVerificationService.Challenge;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class DeviceVerificationServiceImplTest {

    private static final String EMAIL = "user@example.com";
    private static final String COOLDOWN_KEY = "auth:device_otp_cooldown:" + EMAIL;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender mailSender;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DeviceVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DeviceVerificationServiceImpl(
                userRepository,
                redisTemplate,
                passwordEncoder,
                mailSender,
                objectMapper);
        ReflectionTestUtils.setField(service, "otpTtlSeconds", 300L);
        ReflectionTestUtils.setField(service, "requestCooldownSeconds", 60L);
        ReflectionTestUtils.setField(service, "mailFrom", "no-reply@pji.local");
    }

    @Test
    void startChallengeDuringCooldownReturnsExistingActiveChallenge() throws Exception {
        String existingChallengeId = "existing-challenge-id";
        Challenge existingChallenge = new Challenge(EMAIL, "device-1", "Mozilla", "127.0.0.1");

        when(userRepository.findByEmail(EMAIL)).thenReturn(user());
        when(valueOperations.get(COOLDOWN_KEY)).thenReturn(existingChallengeId);
        when(valueOperations.get("auth:device_challenge:" + existingChallengeId))
                .thenReturn(objectMapper.writeValueAsString(existingChallenge));

        String challengeId = service.startChallenge(" USER@example.com ", "device-2", "Chrome", "10.0.0.1");

        assertEquals(existingChallengeId, challengeId);
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        verify(passwordEncoder, never()).encode(anyString());
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void startChallengeIgnoresStaleLegacyCooldownKey() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(user());
        when(valueOperations.get(COOLDOWN_KEY)).thenReturn("1");
        when(valueOperations.get("auth:device_challenge:1")).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-otp");
        when(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), anyString(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doNothing().when(mailSender).send(any(MimeMessage.class));

        String challengeId = service.startChallenge(EMAIL, "device-2", "Chrome", "10.0.0.1");

        assertFalse(challengeId.isBlank());
        verify(redisTemplate).delete(COOLDOWN_KEY);
        verify(mailSender).send(any(MimeMessage.class));
    }

    private User user() {
        User user = new User();
        user.setEmail(EMAIL);
        return user;
    }
}
