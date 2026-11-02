package com.voyago.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CouponQuoteRequest(
    @NotBlank(message = "請輸入優惠碼") @Size(max = 30, message = "優惠碼最多 30 字") String code,
    @NotNull Long routeId,
    @NotNull @Min(value = 1, message = "人數至少 1 人") @Max(value = 20, message = "單筆訂單最多 20 人") Integer people
) {}
