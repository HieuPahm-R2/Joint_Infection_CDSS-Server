package com.vietnam.pji.config.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.vietnam.pji.security.ratelimit.RateLimitFilter;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class WebSecurityConfiguration {
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration authenticationConfiguration,
                        AuthEntryPointConfig authEntryPointConfig)
                        throws Exception {
                return authenticationConfiguration.getAuthenticationManager();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http,
                                               AuthEntryPointConfig authEntryPointConfig,
                                               RateLimitFilter rateLimitFilter,
                                               ActiveSessionFilter activeSessionFilter)
                        throws Exception {
                String[] whileList = {
                                "/", "/api/v1/", "/ws/**",
                                "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/register",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                                "/api/v1/auth/verify-device",
                                "/storage/**",
                                "/actuator/health",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                };
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .cors(Customizer.withDefaults()) // This will use the CorsConfigure bean
                                .authorizeHttpRequests(
                                                authz -> authz
                                                                .requestMatchers(
                                                                                HttpMethod.GET,
                                                                                "/api/v1/upload-sessions/*/validate")
                                                                .permitAll()
                                                                .requestMatchers(
                                                                                HttpMethod.POST,
                                                                                "/api/v1/upload-sessions/*/presigned-url",
                                                                                "/api/v1/upload-sessions/*/complete")
                                                                .permitAll()
                                                                .requestMatchers(whileList).permitAll()
                                                                .anyRequest().authenticated())
                                .oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults())
                                                .authenticationEntryPoint(authEntryPointConfig))
                                // Default config

                                .formLogin(AbstractHttpConfigurer::disable)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .addFilterAfter(activeSessionFilter, BearerTokenAuthenticationFilter.class)
                                .addFilterAfter(rateLimitFilter, ActiveSessionFilter.class);
                return http.build();
        }
}
