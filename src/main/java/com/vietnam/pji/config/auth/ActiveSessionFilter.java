package com.vietnam.pji.config.auth;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.services.feat.RedisService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces single-device login. The login / verify-device flow stores a
 * session id (UUID) in Redis under auth:active_session:{email} and embeds it
 * as the "sid" claim of every issued JWT. When a different device logs in
 * the Redis value is overwritten — this filter then rejects requests whose
 * JWT carries the old sid, so the previous device is kicked out as soon as
 * it sends its next request (rather than waiting for the access token to
 * naturally expire).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveSessionFilter extends OncePerRequestFilter {

    public static final String SESSION_REVOKED_CODE = "SESSION_REVOKED";
    public static final String SESSION_REVOKED_MESSAGE = "Tài khoản đã được đăng nhập trên thiết bị khác.";

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt) {

            String tokenSid = jwt.getClaimAsString("sid");
            String email = jwt.getSubject();
            // Tokens issued before this feature existed have no sid — let them
            // through so existing logged-in sessions don't get force-logged-out
            // by a hot deploy. New tokens always carry a sid.
            if (tokenSid != null && !tokenSid.isBlank() && email != null && !email.isBlank()) {
                String activeSid = redisService.getActiveSession(email);
                if (activeSid != null && !activeSid.equals(tokenSid)) {
                    // hàm này sẽ từ chối và ngắt request
                    writeSessionRevoked(request, response);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeSessionRevoked(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = Map.of(
                "timestamp", new Date(),
                "status", HttpStatus.UNAUTHORIZED.value(),
                "path", request.getRequestURI(),
                "code", SESSION_REVOKED_CODE,
                "error", "Session revoked",
                "message", SESSION_REVOKED_MESSAGE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
