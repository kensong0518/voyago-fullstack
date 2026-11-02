package com.voyago.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CouponUpsertRequest(
    @NotBlank(message = "請填寫優惠碼")
    @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "優惠碼請用 3–30 個英數字、底線或連字號")
    String code,
    @Size(max = 100, message = "說明最多 100 字") String description,
    @NotBlank @Pattern(regexp = "^(PERCENT|AMOUNT)$", message = "折扣類型只能是 PERCENT 或 AMOUNT")
    String discountType,
    @NotNull(message = "請填寫折扣") @Min(value = 1, message = "折扣至少 1") @Max(value = 1_000_000, message = "折扣太大")
    Integer discountValue,
    @Min(value = 0, message = "最低消費不能是負數") Integer minAmount,
    @Min(value = 1, message = "使用上限至少 1 次（不限請留空）") Integer maxUses,
    LocalDateTime expiresAt,
    Boolean active
) {}
