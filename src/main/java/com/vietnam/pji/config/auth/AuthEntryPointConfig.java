package com.vietnam.pji.config.auth;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;

import com.vietnam.pji.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// chuẩn hóa phản hồi khi xác thực thất bại.
@Component
public class AuthEntryPointConfig implements AuthenticationEntryPoint {
    private final AuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    // mapper dùng để convert sang kiểu object
    private final ObjectMapper mapper;

    public AuthEntryPointConfig(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        this.delegate.commence(request, response, authException);
        response.setContentType("application/json;charset=UTF-8"); // support vietnamese language

        ErrorResponse res = new ErrorResponse();
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        res.setTimestamp(new Date());
        res.setPath(request.getRequestURI());
        // handle error NULL value when not send anything
        String errorMess = Optional.ofNullable(authException.getCause())
                .map(Throwable::getMessage)
                .orElse(authException.getMessage());

        res.setError(errorMess);
        res.setMessage("Token not valid !! (This could be due to incorrect formatting or expiration)");
        mapper.writeValue(response.getWriter(), res);
    }
}
