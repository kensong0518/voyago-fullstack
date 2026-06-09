package com.voyago.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "請輸入姓名") @Size(min = 2, max = 50, message = "姓名長度需為 2-50 字") String name,
    @NotBlank @Email(message = "Email 格式不正確") @Size(max = 120) String email,
    @NotBlank
    @Size(min = 8, max = 100, message = "密碼至少 8 碼")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密碼需包含英文字母與數字")
    String password,
    @Pattern(regexp = "^$|^[0-9+\\-() ]{6,30}$", message = "電話格式不正確") String phone
) {}
