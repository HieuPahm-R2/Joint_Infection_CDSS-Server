package com.vietnam.pji.config.auth;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.vietnam.pji.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class JwtConfiguration {

    private static final int HS512_MINIMUM_KEY_BYTES = 64;

    @Value("${secure.jwt.access-base64-secret}")
    private String accessJwtKey;

    @Value("${secure.jwt.refresh-base64-secret}")
    private String refreshJwtKey;

    /*
     * Gắn token với đúng ứng dụng này.
     * trước createDefault() nên bất kỳ token nào ký cùng khoá — kể cả từ service
     * khác trong tương lai — đều được nhận
     */
    @Value("${secure.jwt.issuer}")
    private String issuer;

    /*
     * cấp token dùng cho nhiều resource: aud: ["pji-api", "reporting-service"] mà
     * không cần đổi key
     */
    @Value("${secure.jwt.access-audience}")
    private String accessAudience;

    @Value("${secure.jwt.refresh-audience}")
    private String refreshAudience;

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return decoder(
                getSecretKey(accessJwtKey, "secure.jwt.access-base64-secret"),
                accessAudience,
                SecurityUtils.ACCESS_TOKEN_USE);
    }

    @Bean("refreshJwtDecoder")
    public JwtDecoder refreshJwtDecoder() {
        return decoder(
                getSecretKey(refreshJwtKey, "secure.jwt.refresh-base64-secret"),
                refreshAudience,
                SecurityUtils.REFRESH_TOKEN_USE);
    }

    @Bean("accessJwtEncoder")
    public JwtEncoder accessJwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(
                getSecretKey(accessJwtKey, "secure.jwt.access-base64-secret")));
    }

    @Bean("refreshJwtEncoder")
    public JwtEncoder refreshJwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(
                getSecretKey(refreshJwtKey, "secure.jwt.refresh-base64-secret")));
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName(SecurityUtils.ROLES_CLAIM);

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    private JwtDecoder decoder(SecretKey secretKey, String audience, String tokenUse) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(SecurityUtils.JWT_ALGORITHM)
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud",
                audiences -> audiences != null && audiences.contains(audience));
        OAuth2TokenValidator<Jwt> tokenUseValidator = new JwtClaimValidator<>(
                SecurityUtils.TOKEN_USE_CLAIM,
                tokenUse::equals);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator,
                tokenUseValidator));
        return decoder;
    }

    private SecretKey getSecretKey(String encodedKey, String propertyName) {
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        if (keyBytes.length < HS512_MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(propertyName + " must decode to at least 64 bytes for HS512");
        }
        return new SecretKeySpec(keyBytes, SecurityUtils.JWT_ALGORITHM.getName());
    }
}
