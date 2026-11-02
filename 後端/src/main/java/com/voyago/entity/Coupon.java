package com.voyago.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 優惠碼。discountType：PERCENT（打折，value=10 表示 9 折）或 AMOUNT（折抵固定金額）。
 * usedCount 只用「條件式 UPDATE」遞增（見 CouponRepository.tryRedeem），兩個人同時用最後一張也不會超賣。
 */
@Entity
@Table(name = "coupon")
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String description = "";

    @Column(name = "discount_type", nullable = false, length = 10)
    private String discountType;

    @Column(name = "discount_value", nullable = false)
    private Integer discountValue;

    @Column(name = "min_amount", nullable = false)
    private Integer minAmount = 0;

    @Column(name = "max_uses")
    private Integer maxUses;          // null = 不限次數

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;  // null = 不會過期

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public Integer getDiscountValue() { return discountValue; }
    public void setDiscountValue(Integer discountValue) { this.discountValue = discountValue; }
    public Integer getMinAmount() { return minAmount; }
    public void setMinAmount(Integer minAmount) { this.minAmount = minAmount; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
