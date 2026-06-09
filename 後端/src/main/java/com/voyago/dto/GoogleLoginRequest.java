package com.voyago.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
    @NotBlank(message = "缺少 Google 憑證") String credential
) {}
