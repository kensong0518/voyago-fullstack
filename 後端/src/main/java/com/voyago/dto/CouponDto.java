package com.voyago.dto;

import com.voyago.entity.Coupon;
import java.time.LocalDateTime;

public record CouponDto(Long id, String code, String description, String discountType, Integer discountValue,
                        Integer minAmount, Integer maxUses, Integer usedCount, LocalDateTime expiresAt,
                        Boolean active, LocalDateTime createdAt) {
    public static CouponDto from(Coupon c) {
        return new CouponDto(c.getId(), c.getCode(), c.getDescription(), c.getDiscountType(), c.getDiscountValue(),
                c.getMinAmount(), c.getMaxUses(), c.getUsedCount(), c.getExpiresAt(), c.getActive(), c.getCreatedAt());
    }
}
