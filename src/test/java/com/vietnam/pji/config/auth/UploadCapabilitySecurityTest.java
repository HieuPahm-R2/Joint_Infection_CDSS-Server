package com.vietnam.pji.config.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietnam.pji.constant.UploadSessionStatus;
import com.vietnam.pji.controller.medical.UploadSessionController;
import com.vietnam.pji.dto.response.*;
import com.vietnam.pji.exception.GlobalExceptionHandler;
import com.vietnam.pji.exception.UploadSessionUnauthorizedException;
import com.vietnam.pji.security.ratelimit.RateLimitFilter;
import com.vietnam.pji.security.ratelimit.RateLimitProperties;
import com.vietnam.pji.services.auth.UserService;
import com.vietnam.pji.services.feat.RedisService;
import com.vietnam.pji.services.upload.UploadSessionService;

@SpringJUnitConfig(UploadCapabilitySecurityTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = "api.prefix=/api/v1")
class UploadCapabilitySecurityTest {
    private static final UUID ID = UUID.fromString("b4ecb937-71b3-49a6-8d95-5bc6bf1fd773");
    private static final String CODE = "c".repeat(43);
    private static final String TOKEN = "t".repeat(43);
    private static final String BASE = "/api/v1/upload-sessions/" + ID;

    @Autowired WebApplicationContext context;
    @Autowired AtomicInteger jwtDecodes;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        jwtDecodes.set(0);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @Test
    void anonymousExchangeWorksThroughRealSecurityAndPermissionInterceptor() throws Exception {
        mvc.perform(post(BASE + "/exchange").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.data.uploadToken").value(TOKEN));
        assertThat(jwtDecodes.get()).isZero();
    }

    @Test
    void opaqueBearerWorksOnlyOnCapabilityEndpointsAndIsNotDecodedAsJwt() throws Exception {
        mvc.perform(get(BASE + "/validate").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post(BASE + "/presigned-url").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"result.jpg\",\"contentType\":\"image/jpeg\",\"size\":128}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/complete").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isAccepted());
        assertThat(jwtDecodes.get()).isZero();

        mvc.perform(get(BASE + "/events").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/private").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
        assertThat(jwtDecodes.get()).isEqualTo(2);
        mvc.perform(get(BASE + "/events")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/validate")).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyQueryBodyAndMissingCredentialsCannotAuthorizeUploads() throws Exception {
        mvc.perform(get(BASE + "/validate").param("token", TOKEN)).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post(BASE + "/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/validate").header("Authorization", "Bearer " + CODE))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/exchange").param("code", CODE)).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/exchange").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({WebSecurityConfiguration.class, InterceptorConfiguration.class})
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean AuthEntryPointConfig entryPoint(ObjectMapper mapper) { return new AuthEntryPointConfig(mapper); }
        @Bean AtomicInteger jwtDecodes() { return new AtomicInteger(); }
        @Bean JwtDecoder decoder(AtomicInteger calls) {
            return token -> { calls.incrementAndGet(); throw new BadJwtException("Not a doctor JWT"); };
        }
        @Bean JwtAuthenticationConverter converter() { return new JwtAuthenticationConverter(); }
        @Bean org.springframework.security.authentication.AuthenticationProvider accountAuthenticationProvider() {
            return new org.springframework.security.authentication.AuthenticationProvider() {
                @Override
                public org.springframework.security.core.Authentication authenticate(
                        org.springframework.security.core.Authentication authentication) {
                    throw new org.springframework.security.authentication.BadCredentialsException("Unknown test account");
                }
                @Override
                public boolean supports(Class<?> authentication) {
                    return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class
                            .isAssignableFrom(authentication);
                }
            };
        }
        @Bean ActiveSessionFilter activeSessionFilter(ObjectMapper mapper) { return new ActiveSessionFilter(null, mapper); }
        @Bean RateLimitFilter rateLimitFilter(ObjectMapper mapper) {
            RateLimitProperties props = new RateLimitProperties();
            props.setEnabled(false);
            return new RateLimitFilter(props, null, mapper);
        }
        @Bean UserService userService() { return unusedProxy(UserService.class); }
        @Bean RedisService redisService() { return unusedProxy(RedisService.class); }
        @Bean GlobalExceptionHandler advice() { return new GlobalExceptionHandler(); }
        @Bean UploadSessionController controller() { return new UploadSessionController(new StubUploadService()); }
        @Bean GuardedEndpoints guardedEndpoints() { return new GuardedEndpoints(); }

        private static <T> T unusedProxy(Class<T> type) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> type.getSimpleName() + "TestProxy";
                            };
                        }
                        throw new AssertionError("Capability request reached account permissions");
                    }));
        }
    }

    @RestController
    static class GuardedEndpoints {
        @GetMapping({"/api/v1/upload-sessions/{id}/events", "/api/v1/private"})
        public String protectedEndpoint() { return "protected"; }
    }

    static class StubUploadService extends UploadSessionService {
        StubUploadService() { super(null, null, null, null, null, null, null); }
        private void check(String token) {
            if (!TOKEN.equals(token)) throw new UploadSessionUnauthorizedException("Invalid upload token");
        }
        @Override public ExchangeUploadSessionResponseDTO exchange(UUID id, String code) {
            if (!CODE.equals(code)) throw new UploadSessionUnauthorizedException("Invalid code");
            return new ExchangeUploadSessionResponseDTO(TOKEN, Instant.now().plusSeconds(240));
        }
        @Override public UploadSessionValidationResponseDTO validate(UUID id, String token) {
            check(token);
            return new UploadSessionValidationResponseDTO(id, Instant.now().plusSeconds(240), 10, 1024, Set.of("image/jpeg"));
        }
        @Override public PresignedUploadResponseDTO presign(UUID id, String token, String name, String contentType, long size) {
            check(token);
            return new PresignedUploadResponseDTO(UUID.randomUUID(), "https://storage.test/put", "PUT", contentType, Instant.now());
        }
        @Override public UploadSessionCompleteResponseDTO complete(UUID id, String token) {
            check(token);
            return new UploadSessionCompleteResponseDTO(id, UploadSessionStatus.UPLOADED, 1);
        }
    }
}
