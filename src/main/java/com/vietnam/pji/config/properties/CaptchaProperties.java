package com.vietnam.pji.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.captcha")
public class CaptchaProperties {

    private boolean enabled = false;

    /**
     * Supported values: turnstile, recaptcha.
     */
    private String provider = "turnstile";

    private String secretKey;

    private String turnstileVerifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private String recaptchaVerifyUrl = "https://www.google.com/recaptcha/api/siteverify";

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(3);

    /**
     * Optional. Useful when Google reCAPTCHA v3 is used.
     */
    private Double minScore;

    /**
     * Optional. Useful when Google reCAPTCHA v3 action binding is used.
     */
    private String expectedAction;
}
