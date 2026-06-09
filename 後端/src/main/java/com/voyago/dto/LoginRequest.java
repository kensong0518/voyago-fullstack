package com.voyago.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank @Email(message = "Email 格式不正確") String email,
    @NotBlank(message = "請輸入密碼") String password
) {}
