package com.voyago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CouponApplyRequest(
    @NotBlank(message = "請輸入優惠碼") @Size(max = 30, message = "優惠碼最多 30 字") String code
) {}
