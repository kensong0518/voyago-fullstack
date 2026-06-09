package com.voyago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageRequest(
    @NotBlank(message = "訊息不可為空") @Size(max = 1000) String content
) {}
