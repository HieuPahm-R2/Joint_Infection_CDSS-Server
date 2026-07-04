package com.vietnam.pji.services.security;

public interface CaptchaService {

    void verify(String token, String remoteIp);
}
