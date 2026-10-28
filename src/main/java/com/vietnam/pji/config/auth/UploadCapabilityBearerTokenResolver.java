package com.vietnam.pji.config.auth;

import java.util.List;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletRequest;

/** Opaque upload capabilities are checked by UploadSessionService, never by the JWT decoder. */
public final class UploadCapabilityBearerTokenResolver implements BearerTokenResolver {
    private static final List<RequestMatcher> MOBILE_REQUESTS = List.of(
            new AntPathRequestMatcher("/api/v1/upload-sessions/*/exchange", "POST"),
            new AntPathRequestMatcher("/api/v1/upload-sessions/*/validate", "GET"),
            new AntPathRequestMatcher("/api/v1/upload-sessions/*/presigned-url", "POST"),
            new AntPathRequestMatcher("/api/v1/upload-sessions/*/complete", "POST"));

    private final DefaultBearerTokenResolver jwtResolver = new DefaultBearerTokenResolver();

    public static boolean isCapabilityRequest(HttpServletRequest request) {
        return MOBILE_REQUESTS.stream().anyMatch(matcher -> matcher.matches(request));
    }

    @Override
    public String resolve(HttpServletRequest request) {
        return isCapabilityRequest(request) ? null : jwtResolver.resolve(request);
    }
}
