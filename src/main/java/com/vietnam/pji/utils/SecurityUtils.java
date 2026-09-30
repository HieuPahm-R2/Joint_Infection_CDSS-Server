package com.vietnam.pji.utils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.vietnam.pji.dto.response.ResLoginDTO;

@Service
public class SecurityUtils {
    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;
    public static final String ACCESS_TOKEN_USE = "access";
    public static final String REFRESH_TOKEN_USE = "refresh";
    public static final String TOKEN_USE_CLAIM = "token_use";
    public static final String ROLES_CLAIM = "roles";

    private final JwtEncoder accessJwtEncoder;
    private final JwtEncoder refreshJwtEncoder;

    @Value("${secure.jwt.access-token-validity-in-seconds}")
    private long accessTokenExpire;

    @Value("${secure.jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenExpire;

    @Value("${secure.jwt.issuer}")
    private String issuer;

    @Value("${secure.jwt.access-audience}")
    private String accessAudience;

    @Value("${secure.jwt.refresh-audience}")
    private String refreshAudience;

    public SecurityUtils(
            @Qualifier("accessJwtEncoder") JwtEncoder accessJwtEncoder,
            @Qualifier("refreshJwtEncoder") JwtEncoder refreshJwtEncoder) {
        this.accessJwtEncoder = accessJwtEncoder;
        this.refreshJwtEncoder = refreshJwtEncoder;
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * @return the login of the current user.
     */
    public static Optional<String> getCurrentUserLogin() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(extractPrincipal(securityContext.getAuthentication()));
    }

    /**
     * @return the ID of the current authenticated user, extracted from the JWT "user account" claim.
     */
    @SuppressWarnings("unchecked")
    public static Long getCurrentUserId() {
        SecurityContext ctx = SecurityContextHolder.getContext();
        Authentication auth = ctx.getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            Map<String, Object> userAccount = jwt.getClaim("user account");
            if (userAccount != null && userAccount.get("id") != null) {
                return Long.valueOf(userAccount.get("id").toString());
            }
        }
        return null;
    }

    /**
     * JWT generate Access Token. `sessionId` is the active-session id checked
     * by ActiveSessionFilter; logging in from a new device rotates this value
     * so the previous device's still-valid access token is rejected on its
     * next request.
     */
    public String generateAccessToken(String email, ResLoginDTO user, String sessionId) {
        ResLoginDTO.InfoWithinToken token = new ResLoginDTO.InfoWithinToken();
        token.setId(user.getUser().getId());
        token.setEmail(user.getUser().getEmail());
        token.setName(user.getUser().getName());
        // assign to claim
        Instant now = Instant.now();
        Instant validity = now.plus(this.accessTokenExpire, ChronoUnit.SECONDS);

        // @formatter:off
        JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .audience(List.of(accessAudience))
        .issuedAt(now)
        .expiresAt(validity)
        .subject(email)
        .id(UUID.randomUUID().toString())
        .claim("user account", token)
        .claim("sid", sessionId)
        .claim(TOKEN_USE_CLAIM, ACCESS_TOKEN_USE)
        .claim(ROLES_CLAIM, List.of(user.getUser().getRole().getName()))
        .build();
        // No "typ" header on purpose. Setting it to "at+jwt" (as RFC 9068 suggests) makes
        // NimbusJwtDecoder reject the token with "JOSE header typ (type) at+jwt not allowed",
        // which breaks every authenticated request. The token type is already enforced by
        // three stronger, independent mechanisms: a separate signing key per type, the
        // "aud" claim, and the "token_use" claim.
        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
        return this.accessJwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

     /**
     * JWT generate Refresh Token. Carries the same `sid` as the matching
     * access token so a stolen refresh token from a revoked device cannot
     * be exchanged for a fresh access token.
     */
    public String generateRefreshToken(String emailLogin, ResLoginDTO resLoginDTO, String sessionId){
        Instant now = Instant.now();
        Instant validity = now.plus(this.refreshTokenExpire, ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .audience(List.of(refreshAudience))
        .issuedAt(now)
        .expiresAt(validity)
        .subject(emailLogin)
        .id(UUID.randomUUID().toString())
        .claim("sid", sessionId)
        .claim(TOKEN_USE_CLAIM, REFRESH_TOKEN_USE)
        .build();
        // See the matching note in generateAccessToken: the "rt+jwt" typ header is rejected
        // by NimbusJwtDecoder, so the refresh token is distinguished by its own signing key,
        // audience and token_use claim instead.
        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
        return this.refreshJwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
