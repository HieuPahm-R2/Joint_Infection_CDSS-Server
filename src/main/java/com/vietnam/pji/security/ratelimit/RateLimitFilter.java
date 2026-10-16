package com.vietnam.pji.security.ratelimit;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.exception.ErrorResponse;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimitProperties props;
    private final ProxyManager<String> rateLimitProxyManager;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger-ui")
                || "/swagger-ui.html".equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        RateLimitProperties.Rule rule = matchRule(request.getRequestURI());
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe;
        String key = compositeKey(rule, request);
        try {
            BucketProxy bucket = rateLimitProxyManager.builder().build(key, () -> bucketConfig(rule));
            probe = bucket.tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException ex) {
            // Fail open: if Redis is unreachable, do not block traffic — log once and pass
            // through.
            log.warn("Rate-limit backend failure (fail-open): rule={} key={} err={}",
                    rule.getName(), key, ex.toString());
            chain.doFilter(request, response);
            return;
        }
        // Nếu còn token, filter cho request đi tiếp và trả các header
        response.setHeader("X-RateLimit-Limit", String.valueOf(rule.getCapacity()));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(probe.getRemainingTokens(), 0L)));

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSec = Math.max(1L, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSec));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = new ErrorResponse();
        body.setTimestamp(Date.from(Instant.now()));
        body.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        body.setError("Too Many Requests");
        body.setPath(request.getRequestURI());
        body.setMessage("Quá nhiều yêu cầu. Vui lòng thử lại sau " + retryAfterSec + " giây.");
        objectMapper.writeValue(response.getWriter(), body);

        // Nếu Redis gặp lỗi runtime, filter ghi log rồi cho request đi tiếp (fail open)
        // để lỗi Redis không chặn toàn bộ traffic
        log.warn("Rate limit hit: rule={} key={} retryAfter={}s path={}",
                rule.getName(), key, retryAfterSec, request.getRequestURI());
    }

    private RateLimitProperties.Rule matchRule(String uri) {
        for (RateLimitProperties.Rule r : props.getRules()) {
            for (String pattern : r.getPaths()) {
                if (PATH_MATCHER.match(pattern, uri)) {
                    return r;
                }
            }
        }
        return null;
    }

    private BucketConfiguration bucketConfig(RateLimitProperties.Rule rule) {
        Bandwidth bw = Bandwidth.builder()
                .capacity(rule.getCapacity())
                .refillGreedy(rule.getRefillTokens(), rule.getRefillPeriod())
                .build();
        return BucketConfiguration.builder().addLimit(bw).build();
    }

    // tạo khóa bucket theo tên quy tắc và đối tượng bị giới hạn.
    private String compositeKey(RateLimitProperties.Rule rule, HttpServletRequest req) {
        return "rl:" + rule.getName() + ":" + principalKey(rule, req);
    }

    // dùng JWT subject hoặc username khi perUser: true và người gọi đã login. Nếu
    // không lấy được người dùng, hoặc perUser: false, nó dùng IP.
    private String principalKey(RateLimitProperties.Rule rule, HttpServletRequest req) {
        if (rule.isPerUser()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null
                    && auth.isAuthenticated()
                    && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
                if (auth instanceof JwtAuthenticationToken jwtAuth) {
                    Jwt token = jwtAuth.getToken();
                    String sub = token.getSubject();
                    if (sub != null && !sub.isBlank()) {
                        return "u:" + sub;
                    }
                }
                String name = auth.getName();
                if (name != null && !name.isBlank()) {
                    return "u:" + name;
                }
            }
        }
        return "ip:" + clientIp(req);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return req.getRemoteAddr();
    }
}
