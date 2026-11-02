package com.voyago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AI 行程顧問的提問。 */
public record AdvisorRequest(
    @NotBlank(message = "請描述你想要的旅程")
    @Size(min = 2, max = 300, message = "需求描述請在 2–300 字之間")
    String query
) {}
