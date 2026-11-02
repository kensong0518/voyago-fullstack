package com.voyago.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
    @NotNull(message = "請選擇星等") @Min(value = 1, message = "星等介於 1 到 5") @Max(value = 5, message = "星等介於 1 到 5")
    Integer rating,
    @NotBlank(message = "請寫下你的心得") @Size(min = 5, max = 500, message = "心得請寫 5 到 500 字")
    String content
) {}
