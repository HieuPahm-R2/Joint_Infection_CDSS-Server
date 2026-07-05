package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequestDTO {
    @NotBlank(message = "email không được để trống")
    @Email(message = "email không hợp lệ")
    private String email;

    private String captchaToken;
}
