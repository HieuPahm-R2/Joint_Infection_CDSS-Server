package com.vietnam.pji.services.security.impl;

import java.util.Locale;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.vietnam.pji.config.properties.CaptchaProperties;
import com.vietnam.pji.exception.InvalidDataException;
import com.vietnam.pji.services.security.CaptchaService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private static final String PROVIDER_TURNSTILE = "turnstile";
    private static final String PROVIDER_RECAPTCHA = "recaptcha";

    private final CaptchaProperties properties;
    private final RestTemplateBuilder restTemplateBuilder;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        if (properties.isEnabled() && !StringUtils.hasText(properties.getSecretKey())) {
            throw new IllegalStateException("CAPTCHA is enabled but app.captcha.secret-key is not configured");
        }
        this.restTemplate = restTemplateBuilder
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .build();
    }

    @Override
    public void verify(String token, String remoteIp) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(token)) {
            throw new InvalidDataException("Vui lòng hoàn thành xác thực CAPTCHA.");
        }

        String provider = provider();
        String verifyUrl = verifyUrl(provider);
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.getSecretKey());
        form.add("response", token);
        if (StringUtils.hasText(remoteIp)) {
            form.add("remoteip", remoteIp);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        JsonNode body;
        try {
            body = restTemplate.postForObject(verifyUrl, new HttpEntity<>(form, headers), JsonNode.class);
        } catch (RestClientException ex) {
            log.warn("CAPTCHA verification request failed: provider={} error={}", provider, ex.toString());
            throw new InvalidDataException("Không thể xác thực CAPTCHA. Vui lòng thử lại sau.");
        }

        if (body == null || !body.path("success").asBoolean(false)) {
            log.warn("CAPTCHA rejected: provider={} errors={}", provider, body == null ? null : body.path("error-codes"));
            throw new InvalidDataException("Xác thực CAPTCHA không hợp lệ. Vui lòng thử lại.");
        }

        validateRecaptchaOptionalFields(provider, body);
    }

    private void validateRecaptchaOptionalFields(String provider, JsonNode body) {
        if (!PROVIDER_RECAPTCHA.equals(provider)) {
            return;
        }
        if (properties.getMinScore() != null && body.has("score")
                && body.path("score").asDouble(0.0d) < properties.getMinScore()) {
            throw new InvalidDataException("Xác thực CAPTCHA không đạt yêu cầu. Vui lòng thử lại.");
        }
        if (StringUtils.hasText(properties.getExpectedAction())
                && !properties.getExpectedAction().equals(body.path("action").asText(null))) {
            throw new InvalidDataException("Xác thực CAPTCHA không hợp lệ. Vui lòng thử lại.");
        }
    }

    private String provider() {
        String provider = properties.getProvider() == null
                ? PROVIDER_TURNSTILE
                : properties.getProvider().trim().toLowerCase(Locale.ROOT);
        if (!PROVIDER_TURNSTILE.equals(provider) && !PROVIDER_RECAPTCHA.equals(provider)) {
            throw new IllegalStateException("Unsupported CAPTCHA provider: " + properties.getProvider());
        }
        return provider;
    }

    private String verifyUrl(String provider) {
        if (PROVIDER_RECAPTCHA.equals(provider)) {
            return properties.getRecaptchaVerifyUrl();
        }
        return properties.getTurnstileVerifyUrl();
    }
}
