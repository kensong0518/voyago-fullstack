package com.voyago.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileUpdateRequest(
    @NotBlank(message = "請輸入姓名") String name,
    String phone
) {}
