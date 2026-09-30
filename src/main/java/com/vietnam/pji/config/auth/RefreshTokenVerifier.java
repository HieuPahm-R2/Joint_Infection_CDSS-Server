package com.vietnam.pji.config.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenVerifier {

    private final JwtDecoder decoder;

    public RefreshTokenVerifier(@Qualifier("refreshJwtDecoder") JwtDecoder decoder) {
        this.decoder = decoder;
    }

    public Jwt decode(String token) {
        return decoder.decode(token);
    }
}
