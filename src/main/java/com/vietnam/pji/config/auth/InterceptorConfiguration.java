package com.vietnam.pji.config.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfiguration implements WebMvcConfigurer {
    @Bean
    AuthorityIntercepter getAuthorityInterceptor() {
        return new AuthorityIntercepter();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] whiteList = {
                "/", "/api/v1/auth/**", "/storage/**",
                "/api/v1/files",
                "/api/v1/upload-sessions/*/events",
                // These endpoints authorize the session capability inside UploadSessionService.
                "/api/v1/upload-sessions/*/exchange",
                "/api/v1/upload-sessions/*/validate",
                "/api/v1/upload-sessions/*/presigned-url",
                "/api/v1/upload-sessions/*/complete",
        };
        registry.addInterceptor(getAuthorityInterceptor()).excludePathPatterns(whiteList);
    }
}
