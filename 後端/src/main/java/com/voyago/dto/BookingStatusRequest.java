package com.voyago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 後台調整訂單狀態。 */
public record BookingStatusRequest(
    @NotBlank(message = "請選擇訂單狀態")
    @Pattern(regexp = "^(PENDING|CONFIRMED|CANCELLED)$", message = "訂單狀態只能是 PENDING、CONFIRMED 或 CANCELLED")
    String status
) {}
